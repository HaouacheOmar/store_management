package com.store.common.events;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Contrat Kafka partagé : noms des topics + payloads (clé du message = storeId). */
public final class Events {

    public static final String SALE_COMPLETED = "sale.completed";
    public static final String SALE_CANCELLED = "sale.cancelled";
    public static final String STOCK_LOW = "stock.low";
    public static final String STOCK_RESTOCKED = "stock.restocked";

    /** Header Kafka portant l'id de l'événement (idempotence côté consommateur). */
    public static final String EVENT_ID_HEADER = "eventId";

    private Events() {
    }

    /** Payload de sale.completed et sale.cancelled. */
    public record SaleEvent(long saleId, long storeId, long cashierId, String cashierName,
                            LocalDate saleDate, BigDecimal total, List<SaleLine> lines) {
    }

    public record SaleLine(long productId, String productName, long categoryId, String categoryName,
                           int qty, BigDecimal unitPrice, int discountPercent, BigDecimal lineTotal) {
    }

    public enum StockAlert { LOW, OUT }

    public record StockLowEvent(long storeId, long productId, String productName,
                                int stockQty, int alertThreshold, StockAlert type) {
    }

    public record StockRestockedEvent(long storeId, long productId, int stockQty, int alertThreshold) {
    }
}
