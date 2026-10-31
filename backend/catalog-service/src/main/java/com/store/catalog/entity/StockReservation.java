package com.store.catalog.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Trace d'une sortie de stock (une vente) : ce qui a été sorti, par qui, et si cela a été rendu. */
@Entity
@Table(name = "stock_reservation")
public class StockReservation {

    public enum Status { RESERVED, RELEASED }

    @Embeddable
    public record Line(long productId, int qty) {
    }

    @Id
    private UUID id = UUID.randomUUID();

    private long storeId;
    private long userId;

    @Enumerated(EnumType.STRING)
    private Status status = Status.RESERVED;

    private Instant createdAt = Instant.now();
    private Instant releasedAt;

    @ElementCollection
    @CollectionTable(name = "stock_reservation_line", joinColumns = @JoinColumn(name = "reservation_id"))
    private List<Line> lines = new ArrayList<>();

    protected StockReservation() {
    }

    public StockReservation(long storeId, long userId, List<Line> lines) {
        this.storeId = storeId;
        this.userId = userId;
        this.lines.addAll(lines);
    }

    public UUID getId() {
        return id;
    }

    public long getStoreId() {
        return storeId;
    }

    public List<Line> getLines() {
        return lines;
    }
}
