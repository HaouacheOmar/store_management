package com.store.common.security;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Appels service → service : on transmet le JWT de l'utilisateur (l'appelé applique les mêmes règles :
 * store, permissions) et le secret de service exigé par InternalApiFilter sur /internal/**.
 * Activé par @Import(FeignJwtForwarding.class).
 */
@Configuration
public class FeignJwtForwarding {

    @Bean
    RequestInterceptor forwardJwt(@Value("${app.security.internal-secret:}") String internalSecret) {
        return template -> {
            if (!internalSecret.isBlank()) template.header(InternalApiFilter.HEADER, internalSecret);
            if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken auth) {
                template.header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.getToken().getTokenValue());
            }
        };
    }
}
