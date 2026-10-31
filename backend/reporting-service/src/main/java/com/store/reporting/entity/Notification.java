package com.store.reporting.entity;

import com.store.common.events.Events.StockAlert;
import jakarta.persistence.*;

import java.time.Instant;

/** Alerte « à restocker » d'un produit ; ouverte tant que resolvedAt est vide. */
@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long storeId;
    private long productId;
    private String productName;

    @Enumerated(EnumType.STRING)
    private StockAlert type;

    private int stockQty;
    private Instant resolvedAt;
    private Instant createdAt = Instant.now();

    protected Notification() {
    }

    public Notification(long storeId, long productId, String productName, StockAlert type, int stockQty) {
        this.storeId = storeId;
        this.productId = productId;
        this.productName = productName;
        this.type = type;
        this.stockQty = stockQty;
    }

    /** Même alerte toujours ouverte : on actualise la quantité, l'état lu des utilisateurs est conservé. */
    public void refresh(String productName, int stockQty) {
        this.productName = productName;
        this.stockQty = stockQty;
    }

    public void resolve(Instant at) {
        resolvedAt = at;
    }

    public Long getId() {
        return id;
    }

    public long getStoreId() {
        return storeId;
    }

    public long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public StockAlert getType() {
        return type;
    }

    public int getStockQty() {
        return stockQty;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
