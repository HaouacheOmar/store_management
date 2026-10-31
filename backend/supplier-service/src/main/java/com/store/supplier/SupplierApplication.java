package com.store.supplier;

import com.store.common.security.JwtSecurityConfig;
import com.store.common.web.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({JwtSecurityConfig.class, GlobalExceptionHandler.class})
public class SupplierApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupplierApplication.class, args);
    }
}
