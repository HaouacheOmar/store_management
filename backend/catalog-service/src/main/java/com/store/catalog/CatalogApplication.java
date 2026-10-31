package com.store.catalog;

import com.store.common.security.JwtSecurityConfig;
import com.store.common.web.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@SpringBootApplication
@Import({JwtSecurityConfig.class, GlobalExceptionHandler.class})
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class CatalogApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogApplication.class, args);
    }
}
