package com.store.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Enregistre un événement dans outbox_event, dans la transaction métier en cours. */
public class OutboxPublisher {

    private final EntityManager em;
    private final ObjectMapper json;

    public OutboxPublisher(EntityManager em, ObjectMapper json) {
        this.em = em;
        this.json = json;
    }

    /** MANDATORY : appelé hors transaction, l'événement pourrait exister sans la donnée métier. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String topic, long storeId, Object event) {
        try {
            em.persist(new OutboxEvent(topic, String.valueOf(storeId), json.writeValueAsString(event)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Événement non sérialisable : " + event, e);
        }
    }
}
