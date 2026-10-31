package com.store.sales.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Une vente = un ticket de caisse (id = n° de ticket). Paiement en espèces uniquement (v1). */
@Entity
@Table(name = "sale")
public class Sale {

    public enum Status { COMPLETED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long storeId;
    private long cashierId;
    private String cashierName;
    private LocalDate saleDate;
    private BigDecimal total = BigDecimal.ZERO;
    private BigDecimal cashReceived;
    private BigDecimal changeGiven;

    @Enumerated(EnumType.STRING)
    private Status status = Status.COMPLETED;

    private Long cancelledBy;
    private Instant cancelledAt;
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<SaleLine> lines = new ArrayList<>();

    protected Sale() {
    }

    public Sale(long storeId, long cashierId, String cashierName, LocalDate saleDate) {
        this.storeId = storeId;
        this.cashierId = cashierId;
        this.cashierName = cashierName;
        this.saleDate = saleDate;
    }

    public void addLine(SaleLine line) {
        lines.add(line);
        total = total.add(line.getLineTotal());
    }

    public void pay(BigDecimal cash) {
        cashReceived = cash;
        changeGiven = cash.subtract(total);
    }

    public Long getId() {
        return id;
    }

    public long getStoreId() {
        return storeId;
    }

    public long getCashierId() {
        return cashierId;
    }

    public String getCashierName() {
        return cashierName;
    }

    public LocalDate getSaleDate() {
        return saleDate;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public BigDecimal getCashReceived() {
        return cashReceived;
    }

    public BigDecimal getChangeGiven() {
        return changeGiven;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<SaleLine> getLines() {
        return lines;
    }
}
