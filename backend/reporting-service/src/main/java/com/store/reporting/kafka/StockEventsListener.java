package com.store.reporting.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.common.events.Events;
import com.store.common.events.Events.StockAlert;
import com.store.common.events.Events.StockLowEvent;
import com.store.common.events.Events.StockRestockedEvent;
import com.store.common.idempotence.ProcessedEvents;
import com.store.reporting.entity.Notification;
import com.store.reporting.repository.NotificationRepository;
import com.store.reporting.websocket.NotificationPush;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Alertes « à restocker » (PRD M10) : une seule notification ouverte par produit et par type.
 * Rupture et stock bas s'excluent ; un restockage au-dessus du seuil ferme tout.
 */
@Component
public class StockEventsListener {

    private final NotificationRepository notifications;
    private final ProcessedEvents processed;
    private final NotificationPush push;
    private final ObjectMapper json;

    public StockEventsListener(NotificationRepository notifications, ProcessedEvents processed,
                               NotificationPush push, ObjectMapper json) {
        this.notifications = notifications;
        this.processed = processed;
        this.push = push;
        this.json = json;
    }

    @KafkaListener(topics = {Events.STOCK_LOW, Events.STOCK_RESTOCKED})
    @Transactional
    public void onStockEvent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        var header = record.headers().lastHeader(Events.EVENT_ID_HEADER);
        if (!processed.firstTime(UUID.fromString(new String(header.value(), StandardCharsets.UTF_8)))) return;

        long storeId;
        if (Events.STOCK_LOW.equals(record.topic())) {
            StockLowEvent e = json.readValue(record.value(), StockLowEvent.class);
            storeId = e.storeId();
            // OUT remplace LOW (et inversement quand il reste du stock)
            resolve(storeId, e.productId(), e.type() == StockAlert.OUT ? StockAlert.LOW : StockAlert.OUT);
            open(storeId, e.productId(), e.productName(), e.type(), e.stockQty());
        } else {
            StockRestockedEvent e = json.readValue(record.value(), StockRestockedEvent.class);
            storeId = e.storeId();
            if (e.stockQty() > e.alertThreshold()) {
                notifications.findByStoreIdAndProductIdAndResolvedAtIsNull(storeId, e.productId())
                        .forEach(n -> n.resolve(Instant.now()));
            } else if (e.stockQty() > 0) {
                // restocké mais toujours sous le seuil : une rupture ouverte devient un stock bas
                var out = notifications.findByStoreIdAndProductIdAndTypeAndResolvedAtIsNull(storeId, e.productId(), StockAlert.OUT);
                out.ifPresent(n -> {
                    n.resolve(Instant.now());
                    open(storeId, e.productId(), n.getProductName(), StockAlert.LOW, e.stockQty());
                });
                if (out.isEmpty()) {
                    notifications.findByStoreIdAndProductIdAndTypeAndResolvedAtIsNull(storeId, e.productId(), StockAlert.LOW)
                            .ifPresent(n -> n.refresh(n.getProductName(), e.stockQty()));
                }
            }
        }
        push.storeChangedAfterCommit(storeId);
    }

    private void open(long storeId, long productId, String productName, StockAlert type, int qty) {
        notifications.findByStoreIdAndProductIdAndTypeAndResolvedAtIsNull(storeId, productId, type)
                .ifPresentOrElse(n -> n.refresh(productName, qty),
                        () -> notifications.save(new Notification(storeId, productId, productName, type, qty)));
    }

    private void resolve(long storeId, long productId, StockAlert type) {
        notifications.findByStoreIdAndProductIdAndTypeAndResolvedAtIsNull(storeId, productId, type)
                .ifPresent(n -> n.resolve(Instant.now()));
    }

}
