package com.store.common.idempotence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Événement Kafka déjà traité par ce service (la clé primaire garantit une seule application). */
@Entity
@Table(name = "processed_event")
public class ProcessedEvent {

    @Id
    private UUID eventId;

    private Instant processedAt = Instant.now();

    protected ProcessedEvent() {
    }

    public ProcessedEvent(UUID eventId) {
        this.eventId = eventId;
    }
}
