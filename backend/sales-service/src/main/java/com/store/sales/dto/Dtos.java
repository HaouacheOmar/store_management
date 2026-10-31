package com.store.sales.dto;

import com.store.sales.entity.Sale;
import com.store.sales.entity.SaleLine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class Dtos {

    private Dtos() {
    }

    public record LineRequest(@NotNull Long productId, @Positive @Max(10_000) int qty) {
    }

    /** Prix et remises ne viennent jamais du client : le catalogue les fixe au moment de la vente. */
    public record SaleRequest(@NotEmpty @Size(max = 200) List<@Valid LineRequest> lines,
                              @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal cashReceived) {
    }

    public record LineResponse(long productId, String productName, int qty, BigDecimal unitPrice,
                               int discountPercent, BigDecimal lineTotal) {
        static LineResponse of(SaleLine l) {
            return new LineResponse(l.getProductId(), l.getProductName(), l.getQty(), l.getUnitPrice(),
                    l.getDiscountPercent(), l.getLineTotal());
        }
    }

    /** Ticket complet. */
    public record SaleResponse(long id, long cashierId, String cashierName, Instant createdAt, LocalDate saleDate,
                               Sale.Status status, BigDecimal total, BigDecimal cashReceived, BigDecimal changeGiven,
                               Instant cancelledAt, List<LineResponse> lines) {
        public static SaleResponse of(Sale s) {
            return new SaleResponse(s.getId(), s.getCashierId(), s.getCashierName(), s.getCreatedAt(), s.getSaleDate(),
                    s.getStatus(), s.getTotal(), s.getCashReceived(), s.getChangeGiven(), s.getCancelledAt(),
                    s.getLines().stream().map(LineResponse::of).toList());
        }
    }

    /** Ligne de la liste des ventes du jour. */
    public record SaleSummary(long id, Instant createdAt, long cashierId, String cashierName, BigDecimal total,
                              int itemCount, Sale.Status status) {
        public static SaleSummary of(Sale s) {
            return new SaleSummary(s.getId(), s.getCreatedAt(), s.getCashierId(), s.getCashierName(), s.getTotal(),
                    s.getLines().stream().mapToInt(SaleLine::getQty).sum(), s.getStatus());
        }
    }
}
