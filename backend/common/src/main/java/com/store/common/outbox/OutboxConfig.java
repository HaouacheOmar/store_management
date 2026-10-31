package com.store.common.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
}
