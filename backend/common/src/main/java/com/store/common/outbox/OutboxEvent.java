package com.store.common.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Événement Kafka en attente d'envoi, écrit dans la transaction métier (transactional outbox).
 * Les services qui l'utilisent l'ajoutent à leur @EntityScan.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    private UUID id = UUID.randomUUID();

    private String topic;

    /** storeId : tous les événements d'un store vont dans la même partition, donc dans l'ordre. */
    private String msgKey;

    @Column(columnDefinition = "text")
    private String payload;

    private Instant createdAt = Instant.now();
    private Instant sentAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(String topic, String msgKey, String payload) {
        this.topic = topic;
        this.msgKey = msgKey;
        this.payload = payload;
    }

    public void markSent(Instant at) {
        sentAt = at;
    }

    public UUID getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public String getMsgKey() {
        return msgKey;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
