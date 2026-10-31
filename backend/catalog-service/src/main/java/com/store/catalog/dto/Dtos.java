package com.store.catalog.dto;

import com.store.catalog.entity.Discount;
import com.store.catalog.entity.Product;
import com.store.catalog.entity.Restock;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;

public final class Dtos {

    private Dtos() {
    }

    public record CategoryRequest(@NotBlank @Size(max = 80) String name) {
    }

    public record CategoryResponse(long id, String name, long productCount) {
    }

    /** stockQty : quantité initiale, prise en compte uniquement à la création. */
    public record ProductRequest(@NotBlank @Size(max = 120) String name,
                                 @Size(max = 50) String code,
                                 @NotNull Long categoryId,
                                 @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
                                 @PositiveOrZero int stockQty,
                                 @PositiveOrZero int alertThreshold,
                                 Integer version) {
    }

    /** discountPercent / finalPrice : remise active la plus forte au moment de la lecture (affichage seulement). */
    public record ProductResponse(long id, String name, String code, long categoryId, String categoryName,
                                  BigDecimal price, int discountPercent, BigDecimal finalPrice,
                                  int stockQty, int alertThreshold, int version) {
        public static ProductResponse of(Product p, int discountPercent, BigDecimal finalPrice) {
            return new ProductResponse(p.getId(), p.getName(), p.getCode(), p.getCategory().getId(),
                    p.getCategory().getName(), p.getPrice(), discountPercent, finalPrice,
                    p.getStockQty(), p.getAlertThreshold(), p.getVersion());
        }
    }

    public record RestockRequest(@NotNull Long productId,
                                 @NotNull Long supplierId,
                                 @Positive @Max(1_000_000) int qty,
                                 @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal unitCost) {
    }

    public record RestockResponse(long id, long productId, String productName, long supplierId, String supplierName,
                                  int qty, BigDecimal unitCost, String userName, Instant createdAt) {
        public static RestockResponse of(Restock r) {
            return new RestockResponse(r.getId(), r.getProduct().getId(), r.getProduct().getName(), r.getSupplierId(),
                    r.getSupplierName(), r.getQty(), r.getUnitCost(), r.getUserName(), r.getCreatedAt());
        }
    }

    public record DiscountRequest(@NotBlank @Size(max = 120) String label,
                                  @Min(1) @Max(100) int percent,
                                  @NotNull Discount.Target targetType,
                                  @NotNull Long targetId,
                                  @NotNull Instant startsAt,
                                  @NotNull Instant endsAt) {
    }

    public record DiscountResponse(long id, String label, int percent, Discount.Target targetType, long targetId,
                                   String targetName, Instant startsAt, Instant endsAt, Discount.Status status) {
    }

    /** LOW = à restocker (stock ≤ seuil, ruptures comprises) ; OUT = rupture (stock = 0). */
    public enum StockFilter { ALL, LOW, OUT }
}
