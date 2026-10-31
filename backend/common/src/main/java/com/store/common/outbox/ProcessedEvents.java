package com.store.common.outbox;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Idempotence des consommateurs Kafka (livraison at-least-once) : à appeler dans la transaction
 * du traitement ; false = événement déjà traité, ne rien faire. Table processed_event(event_id).
 * Activé par @Import(ProcessedEvents.class).
 */
public class ProcessedEvents {

    private final JdbcTemplate jdbc;

    public ProcessedEvents(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstTime(UUID eventId) {
        return jdbc.update("INSERT INTO processed_event (event_id) VALUES (?) ON CONFLICT DO NOTHING", eventId) == 1;
    }
}
