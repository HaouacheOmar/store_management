package com.store.catalog.dto;

import com.store.catalog.entity.Product;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

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

    public record ProductResponse(long id, String name, String code, long categoryId, String categoryName,
                                  BigDecimal price, int stockQty, int alertThreshold, int version) {
        public static ProductResponse of(Product p) {
            return new ProductResponse(p.getId(), p.getName(), p.getCode(), p.getCategory().getId(),
                    p.getCategory().getName(), p.getPrice(), p.getStockQty(), p.getAlertThreshold(), p.getVersion());
        }
    }

    /** LOW = à restocker (stock ≤ seuil, ruptures comprises) ; OUT = rupture (stock = 0). */
    public enum StockFilter { ALL, LOW, OUT }
}
