package com.store.reporting.entity;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/** « L'utilisateur X a lu la notification Y » : le statut lu est propre à chaque utilisateur (PRD M10). */
@Entity
@Table(name = "notification_read")
public class NotificationRead {

    @Embeddable
    public record Key(long notificationId, long userId) {
    }

    @EmbeddedId
    private Key id;

    private Instant readAt = Instant.now();

    protected NotificationRead() {
    }

    public NotificationRead(Key id) {
        this.id = id;
    }
}
