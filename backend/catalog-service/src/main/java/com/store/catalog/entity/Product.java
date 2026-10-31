package com.store.catalog.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long storeId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    private String name;
    private String code;
    private BigDecimal price;

    /** Ne change que par vente, restockage ou annulation (jamais par l'édition du produit). */
    private int stockQty;

    private int alertThreshold;
    private boolean deleted;

    @Version
    private int version;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    protected Product() {
    }

    public Product(long storeId, Category category, String name, String code, BigDecimal price,
                   int stockQty, int alertThreshold) {
        this.storeId = storeId;
        this.stockQty = stockQty;
        update(category, name, code, price, alertThreshold);
    }

    public void update(Category category, String name, String code, BigDecimal price, int alertThreshold) {
        this.category = category;
        this.name = name;
        this.code = code;
        this.price = price;
        this.alertThreshold = alertThreshold;
        this.updatedAt = Instant.now();
    }

    public void delete() {
        deleted = true;
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Category getCategory() {
        return category;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getStockQty() {
        return stockQty;
    }

    public int getAlertThreshold() {
        return alertThreshold;
    }

    public int getVersion() {
        return version;
    }
}
