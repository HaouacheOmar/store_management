package com.store.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.store.common.security.JwtSecurityConfig;
import com.store.common.web.GlobalExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@SpringBootApplication
@Import({JwtSecurityConfig.class, GlobalExceptionHandler.class})
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Même clé que le JwtDecoder de JwtSecurityConfig, utilisé par tous les services. */
    @Bean
    JwtEncoder jwtEncoder(@Value("${app.security.jwt-secret}") String secret) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(JwtSecurityConfig.secretKey(secret)));
    }
}
