package com.store.catalog.entity;

import jakarta.persistence.*;

import java.time.Instant;

/** Remise ponctuelle (événement daté) sur un produit OU une catégorie. */
@Entity
@Table(name = "discount")
public class Discount {

    public enum Target { PRODUCT, CATEGORY }

    public enum Status { ACTIVE, UPCOMING, PAST }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long storeId;
    private String label;
    private short percent;

    @Enumerated(EnumType.STRING)
    private Target targetType;

    private long targetId;
    private Instant startsAt;
    private Instant endsAt;
    private long createdBy;
    private Instant createdAt = Instant.now();

    protected Discount() {
    }

    public Discount(long storeId, long createdBy) {
        this.storeId = storeId;
        this.createdBy = createdBy;
    }

    public void update(String label, int percent, Target targetType, long targetId, Instant startsAt, Instant endsAt) {
        this.label = label;
        this.percent = (short) percent;
        this.targetType = targetType;
        this.targetId = targetId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public Status status(Instant now) {
        if (now.isBefore(startsAt)) return Status.UPCOMING;
        return now.isBefore(endsAt) ? Status.ACTIVE : Status.PAST;
    }

    /** Termine une remise en cours. */
    public void end(Instant now) {
        endsAt = now;
    }

    public boolean appliesTo(Product product) {
        return targetType == Target.PRODUCT
                ? targetId == product.getId()
                : targetId == product.getCategory().getId();
    }

    public Long getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public int getPercent() {
        return percent;
    }

    public Target getTargetType() {
        return targetType;
    }

    public long getTargetId() {
        return targetId;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }
}
