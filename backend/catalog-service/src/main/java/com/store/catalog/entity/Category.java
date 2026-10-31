package com.store.catalog.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "category")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long storeId;
    private String name;
    private boolean deleted;
    private Instant createdAt = Instant.now();

    protected Category() {
    }

    public Category(long storeId, String name) {
        this.storeId = storeId;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public long getStoreId() {
        return storeId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void delete() {
        deleted = true;
    }
}
