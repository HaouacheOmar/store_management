package com.store.auth.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "store")
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String timezone;
    private String currency;
    private Instant createdAt = Instant.now();

    protected Store() {
    }

    public Store(String name, String timezone, String currency) {
        this.name = name;
        this.timezone = timezone;
        this.currency = currency;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getCurrency() {
        return currency;
    }
}
