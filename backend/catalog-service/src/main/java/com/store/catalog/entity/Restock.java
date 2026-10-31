package com.store.catalog.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/** Historique : qui a restocké quoi, combien, chez quel fournisseur (nom copié, cf. DATABASE_SCHEMA §0). */
@Entity
@Table(name = "restock")
public class Restock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long storeId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    private long supplierId;
    private String supplierName;
    private int qty;
    private BigDecimal unitCost;
    private long userId;
    private String userName;
    private Instant createdAt = Instant.now();

    protected Restock() {
    }

    public Restock(long storeId, Product product, long supplierId, String supplierName, int qty,
                   BigDecimal unitCost, long userId, String userName) {
        this.storeId = storeId;
        this.product = product;
        this.supplierId = supplierId;
        this.supplierName = supplierName;
        this.qty = qty;
        this.unitCost = unitCost;
        this.userId = userId;
        this.userName = userName;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public long getSupplierId() {
        return supplierId;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public int getQty() {
        return qty;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public String getUserName() {
        return userName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
