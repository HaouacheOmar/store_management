package com.store.common.testing;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Postgres + Kafka démarrés une fois par JVM de test, partagés par toutes les classes qui en héritent :
 * les contextes Spring mis en cache (et leurs tâches planifiées) ne voient jamais un conteneur arrêté.
 */
public abstract class SharedContainers {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @ServiceConnection
    protected static final KafkaContainer kafka = new KafkaContainer("apache/kafka:3.9.0")
            // Kafka 3.9 refuse un contrôleur annoncé en 0.0.0.0 (défaut de Testcontainers 1.21) ; le quorum est sur localhost:9094
            .withEnv("KAFKA_LISTENERS", "PLAINTEXT://0.0.0.0:9092,BROKER://0.0.0.0:9093,CONTROLLER://localhost:9094");

    static {
        postgres.start();
        kafka.start();
    }

    /** Premier message du topic (lu depuis le début) qui satisfait le prédicat ; échoue après 30 s. */
    protected static ConsumerRecord<String, String> awaitRecord(String topic, Predicate<ConsumerRecord<String, String>> match) {
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < deadline) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    if (match.test(record)) return record;
                }
            }
        }
        throw new AssertionError("Aucun message attendu reçu sur " + topic);
    }
}
