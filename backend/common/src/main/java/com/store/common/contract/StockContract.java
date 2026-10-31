package com.store.common.contract;

import java.math.BigDecimal;
import java.util.List;

/** Contrat REST interne catalog ← sales (/internal/stock/**). */
public final class StockContract {

    private StockContract() {
    }

    public record Line(long productId, int qty) {
    }

    public record StockRequest(List<Line> lines) {
    }

    /** Prix faisant foi au moment de la vente : finalUnitPrice = unitPrice remisé, arrondi au centime. */
    public record PricedLine(long productId, String productName, long categoryId, String categoryName,
                             BigDecimal unitPrice, int discountPercent, BigDecimal finalUnitPrice) {
    }
}
