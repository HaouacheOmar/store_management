package com.store.catalog;

import com.store.common.idempotence.ProcessedEvent;
import com.store.common.idempotence.ProcessedEvents;
import com.store.common.outbox.OutboxConfig;
import com.store.common.outbox.OutboxEvent;
import com.store.common.security.FeignJwtForwarding;
import com.store.common.security.JwtSecurityConfig;
import com.store.common.web.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@SpringBootApplication
// entités du service + celles du module common (outbox / idempotence Kafka)
@EntityScan(basePackageClasses = {CatalogApplication.class, OutboxEvent.class, ProcessedEvent.class})
@EnableFeignClients
@Import({JwtSecurityConfig.class, GlobalExceptionHandler.class, OutboxConfig.class, ProcessedEvents.class,
        FeignJwtForwarding.class})
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class CatalogApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogApplication.class, args);
    }
}
