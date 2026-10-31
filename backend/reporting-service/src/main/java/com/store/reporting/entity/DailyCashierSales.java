package com.store.reporting.entity;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Encaissé d'un caissier sur une journée du store (modèle de lecture, alimenté par Kafka). */
@Entity
@Table(name = "daily_cashier_sales")
public class DailyCashierSales {

    @Embeddable
    public record Key(long storeId, LocalDate saleDate, long cashierId) {
    }

    @EmbeddedId
    private Key id;

    private String cashierName;
    private int ticketCount;
    private BigDecimal total;

    protected DailyCashierSales() {
    }

    public Key getId() {
        return id;
    }

    public String getCashierName() {
        return cashierName;
    }

    public int getTicketCount() {
        return ticketCount;
    }

    public BigDecimal getTotal() {
        return total;
    }
}
