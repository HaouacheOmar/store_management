package com.store.common.idempotence;

import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Idempotence des consommateurs Kafka (livraison at-least-once) : à appeler dans la transaction
 * du traitement ; false = événement déjà traité, ne rien faire. Activé par @Import(ProcessedEvents.class),
 * avec ProcessedEvent dans l'@EntityScan du service.
 */
public class ProcessedEvents {

    private final EntityManager em;

    public ProcessedEvents(EntityManager em) {
        this.em = em;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstTime(UUID eventId) {
        if (em.find(ProcessedEvent.class, eventId) != null) return false;
        em.persist(new ProcessedEvent(eventId));
        // flush immédiat : un doublon traité en parallèle échoue ici sur la clé primaire, avant tout effet métier,
        // la transaction est annulée et Kafka relivre (le find renverra alors false)
        em.flush();
        return true;
    }
}
