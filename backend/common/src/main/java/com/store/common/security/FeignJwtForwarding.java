package com.store.common.security;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Appels service → service : on transmet le JWT de l'utilisateur, l'appelé applique donc
 * les mêmes règles (store, permissions). Activé par @Import(FeignJwtForwarding.class).
 */
@Configuration
public class FeignJwtForwarding {

    @Bean
    RequestInterceptor forwardJwt() {
        return template -> {
            if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken auth) {
                template.header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.getToken().getTokenValue());
            }
        };
    }
}
