package com.store.common.outbox;

import com.store.common.events.Events;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Envoie à Kafka les lignes non envoyées de outbox_event (livraison at-least-once). */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    record Row(UUID id, String topic, String key, String payload) {
    }

    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;

    public OutboxRelay(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka) {
        this.jdbc = jdbc;
        this.kafka = kafka;
    }

    /** SKIP LOCKED : plusieurs instances du service peuvent tourner sans envoyer deux fois la même ligne. */
    @Scheduled(fixedDelayString = "${app.outbox.poll-ms:500}")
    @Transactional
    public void relay() {
        List<Row> rows = jdbc.query("""
                        SELECT id, topic, msg_key, payload FROM outbox_event
                        WHERE sent_at IS NULL ORDER BY created_at LIMIT 100
                        FOR UPDATE SKIP LOCKED""",
                (rs, i) -> new Row(rs.getObject("id", UUID.class), rs.getString("topic"),
                        rs.getString("msg_key"), rs.getString("payload")));

        for (Row row : rows) {
            var record = new ProducerRecord<>(row.topic(), row.key(), row.payload());
            record.headers().add(Events.EVENT_ID_HEADER, row.id().toString().getBytes(StandardCharsets.UTF_8));
            try {
                kafka.send(record).get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                // on s'arrête pour préserver l'ordre ; les lignes déjà envoyées restent marquées
                log.warn("Outbox : envoi de {} échoué, nouvelle tentative au prochain cycle", row.id(), e);
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                return;
            }
            jdbc.update("UPDATE outbox_event SET sent_at = now() WHERE id = ?", row.id());
        }
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void purgeSent() {
        jdbc.update("DELETE FROM outbox_event WHERE sent_at < now() - interval '7 days'");
    }
}
