package com.store.reporting;

import com.store.common.idempotence.ProcessedEvent;
import com.store.common.idempotence.ProcessedEvents;
import com.store.common.security.JwtSecurityConfig;
import com.store.common.web.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication
// entités du service + celles du module common (outbox / idempotence Kafka)
@EntityScan(basePackageClasses = {ReportingApplication.class, ProcessedEvent.class})
@Import({JwtSecurityConfig.class, GlobalExceptionHandler.class, ProcessedEvents.class})
public class ReportingApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportingApplication.class, args);
    }
}
