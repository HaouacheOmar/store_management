package com.store.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Enregistre un événement dans outbox_event, dans la transaction métier en cours. */
public class OutboxPublisher {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public OutboxPublisher(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** MANDATORY : appelé hors transaction, l'événement pourrait exister sans la donnée métier. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String topic, long storeId, Object event) {
        try {
            jdbc.update("INSERT INTO outbox_event (id, topic, msg_key, payload) VALUES (?, ?, ?, ?)",
                    UUID.randomUUID(), topic, String.valueOf(storeId), json.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Événement non sérialisable : " + event, e);
        }
    }
}
