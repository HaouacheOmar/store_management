package com.store.sales;

import com.store.common.outbox.OutboxConfig;
import com.store.common.security.FeignJwtForwarding;
import com.store.common.security.JwtSecurityConfig;
import com.store.common.web.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@EnableFeignClients
@Import({JwtSecurityConfig.class, GlobalExceptionHandler.class, OutboxConfig.class, FeignJwtForwarding.class})
public class SalesApplication {

    public static void main(String[] args) {
        SpringApplication.run(SalesApplication.class, args);
    }
}
