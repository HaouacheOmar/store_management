package com.store.reporting.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.common.events.Events;
import com.store.common.events.Events.SaleEvent;
import com.store.common.events.Events.SaleLine;
import com.store.common.idempotence.ProcessedEvents;
import com.store.reporting.repository.DailyCashierSalesRepository;
import com.store.reporting.repository.DailyProductSalesRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Vente -> +qty / +montant ; annulation -> les mêmes valeurs en négatif.
 * Additions commutatives : peu importe l'ordre d'arrivée des deux topics, le cumul final est juste.
 */
@Component
public class SaleEventsListener {

    private final DailyProductSalesRepository products;
    private final DailyCashierSalesRepository cashiers;
    private final ProcessedEvents processed;
    private final ObjectMapper json;

    public SaleEventsListener(DailyProductSalesRepository products, DailyCashierSalesRepository cashiers,
                              ProcessedEvents processed, ObjectMapper json) {
        this.products = products;
        this.cashiers = cashiers;
        this.processed = processed;
        this.json = json;
    }

    @KafkaListener(topics = {Events.SALE_COMPLETED, Events.SALE_CANCELLED})
    @Transactional
    public void onSaleEvent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        var header = record.headers().lastHeader(Events.EVENT_ID_HEADER);
        if (!processed.firstTime(UUID.fromString(new String(header.value(), StandardCharsets.UTF_8)))) return;

        SaleEvent sale = json.readValue(record.value(), SaleEvent.class);
        int sign = Events.SALE_COMPLETED.equals(record.topic()) ? 1 : -1;

        for (SaleLine line : sale.lines()) {
            products.add(sale.storeId(), sale.saleDate(), line.productId(), line.productName(), line.categoryId(),
                    line.categoryName(), sign * line.qty(), signed(line.lineTotal(), sign));
        }
        cashiers.add(sale.storeId(), sale.saleDate(), sale.cashierId(), sale.cashierName(), sign,
                signed(sale.total(), sign));
    }

    private static BigDecimal signed(BigDecimal amount, int sign) {
        return sign > 0 ? amount : amount.negate();
    }
}
