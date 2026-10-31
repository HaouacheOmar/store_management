package com.store.common.contract;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Contrat REST interne catalog ← sales (/internal/stock/**). */
public final class StockContract {

    private StockContract() {
    }

    public record Line(long productId, int qty) {
    }

    public record StockRequest(List<Line> lines) {
    }

    /**
     * Résultat d'une sortie de stock. reservationId est le seul moyen d'annuler cette sortie
     * (compensation) : une fois, par le même caissier, dans les minutes qui suivent.
     */
    public record Reservation(UUID reservationId, List<PricedLine> lines) {
    }

    /** Prix faisant foi au moment de la vente : finalUnitPrice = unitPrice remisé, arrondi au centime. */
    public record PricedLine(long productId, String productName, long categoryId, String categoryName,
                             BigDecimal unitPrice, int discountPercent, BigDecimal finalUnitPrice) {
    }
}
