package com.store.common.outbox;

import com.store.common.events.Events;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.hibernate.LockOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Envoie à Kafka les événements non envoyés de outbox_event (livraison at-least-once). */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final EntityManager em;
    private final KafkaTemplate<String, String> kafka;

    public OutboxRelay(EntityManager em, KafkaTemplate<String, String> kafka) {
        this.em = em;
        this.kafka = kafka;
    }

    /**
     * Verrou pessimiste SKIP LOCKED : plusieurs instances du service peuvent tourner sans envoyer
     * deux fois le même événement. sent_at est écrit au commit (dirty checking).
     */
    @Scheduled(fixedDelayString = "${app.outbox.poll-ms:500}")
    @Transactional
    public void relay() {
        List<OutboxEvent> pending = em.createQuery(
                        "select e from OutboxEvent e where e.sentAt is null order by e.createdAt", OutboxEvent.class)
                .setMaxResults(100)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setHint("jakarta.persistence.lock.timeout", LockOptions.SKIP_LOCKED)
                .getResultList();

        for (OutboxEvent event : pending) {
            var record = new ProducerRecord<>(event.getTopic(), event.getMsgKey(), event.getPayload());
            record.headers().add(Events.EVENT_ID_HEADER, event.getId().toString().getBytes(StandardCharsets.UTF_8));
            try {
                kafka.send(record).get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                // on s'arrête pour préserver l'ordre ; les événements déjà envoyés restent marqués
                log.warn("Outbox : envoi de {} échoué, nouvelle tentative au prochain cycle", event.getId(), e);
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                return;
            }
            event.markSent(Instant.now());
        }
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeSent() {
        em.createQuery("delete from OutboxEvent e where e.sentAt < :before")
                .setParameter("before", Instant.now().minus(Duration.ofDays(7)))
                .executeUpdate();
    }
}
