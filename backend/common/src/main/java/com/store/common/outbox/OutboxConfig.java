package com.store.common.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Activé par @Import(OutboxConfig.class) dans les services producteurs (catalog, sales). */
@Configuration
@EnableScheduling
public class OutboxConfig {

    @Bean
    OutboxPublisher outboxPublisher(JdbcTemplate jdbc, ObjectMapper json) {
        return new OutboxPublisher(jdbc, json);
    }

    @Bean
    OutboxRelay outboxRelay(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka) {
        return new OutboxRelay(jdbc, kafka);
    }
}
