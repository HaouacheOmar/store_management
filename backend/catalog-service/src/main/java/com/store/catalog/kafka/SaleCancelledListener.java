package com.store.catalog.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.catalog.service.StockService;
import com.store.common.contract.StockContract.Line;
import com.store.common.events.Events;
import com.store.common.events.Events.SaleEvent;
import com.store.common.outbox.ProcessedEvents;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Vente annulée -> recrédit du stock. Idempotent : un événement relivré n'est appliqué qu'une fois. */
@Component
public class SaleCancelledListener {

    private final StockService stock;
    private final ProcessedEvents processed;
    private final ObjectMapper json;

    public SaleCancelledListener(StockService stock, ProcessedEvents processed, ObjectMapper json) {
        this.stock = stock;
        this.processed = processed;
        this.json = json;
    }

    @KafkaListener(topics = Events.SALE_CANCELLED)
    @Transactional
    public void onSaleCancelled(ConsumerRecord<String, String> record) throws JsonProcessingException {
        var header = record.headers().lastHeader(Events.EVENT_ID_HEADER);
        UUID eventId = UUID.fromString(new String(header.value(), StandardCharsets.UTF_8));
        if (!processed.firstTime(eventId)) return;
        SaleEvent sale = json.readValue(record.value(), SaleEvent.class);
        stock.increment(sale.storeId(), sale.lines().stream().map(l -> new Line(l.productId(), l.qty())).toList());
    }
}
