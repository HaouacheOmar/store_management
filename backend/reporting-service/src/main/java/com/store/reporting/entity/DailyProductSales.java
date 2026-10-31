package com.store.reporting.entity;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Cumul d'un produit sur une journée du store (modèle de lecture, alimenté par Kafka). */
@Entity
@Table(name = "daily_product_sales")
public class DailyProductSales {

    @Embeddable
    public record Key(long storeId, LocalDate saleDate, long productId) {
    }

    @EmbeddedId
    private Key id;

    private String productName;
    private long categoryId;
    private String categoryName;
    private int qty;
    private BigDecimal revenue;

    protected DailyProductSales() {
    }

    public Key getId() {
        return id;
    }

    public String getProductName() {
        return productName;
    }

    public long getCategoryId() {
        return categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public int getQty() {
        return qty;
    }

    public BigDecimal getRevenue() {
        return revenue;
    }
}
