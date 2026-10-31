package com.store.supplier.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "supplier")
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long storeId;
    private String name;
    private String phone;
    private String email;
    private String address;
    private boolean deleted;
    private Instant createdAt = Instant.now();

    protected Supplier() {
    }

    public Supplier(long storeId) {
        this.storeId = storeId;
    }

    public void update(String name, String phone, String email, String address) {
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.address = address;
    }

    /** Logique : l'historique des restockages garde le nom du fournisseur. */
    public void delete() {
        deleted = true;
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

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getAddress() {
        return address;
    }
}
