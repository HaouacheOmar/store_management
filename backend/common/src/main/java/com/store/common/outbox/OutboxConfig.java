package com.store.common.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.common.events.Events;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activé par @Import(OutboxConfig.class) dans les services producteurs (catalog, sales),
 * avec OutboxEvent dans leur @EntityScan.
 */
@Configuration
@EnableScheduling
public class OutboxConfig {

    @Bean
    OutboxPublisher outboxPublisher(EntityManager em, ObjectMapper json) {
        return new OutboxPublisher(em, json);
    }

    @Bean
    OutboxRelay outboxRelay(EntityManager em, KafkaTemplate<String, String> kafka) {
        return new OutboxRelay(em, kafka);
    }

    /**
     * Les Kafka managés désactivent souvent la création automatique des topics : on les déclare
     * (création idempotente au démarrage). Facteur de réplication : défaut du broker (1 en local, 3 en cloud).
     */
    @Bean
    KafkaAdmin.NewTopics storeTopics() {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(Events.SALE_COMPLETED).partitions(3).build(),
                TopicBuilder.name(Events.SALE_CANCELLED).partitions(3).build(),
                TopicBuilder.name(Events.STOCK_LOW).partitions(3).build(),
                TopicBuilder.name(Events.STOCK_RESTOCKED).partitions(3).build());
    }
}
