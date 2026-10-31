package com.store.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Chaque service métier valide lui-même le JWT émis par auth-service (HS256, secret partagé).
 * Les permissions du token deviennent des authorities : @PreAuthorize("hasAuthority('PRODUCT_WRITE')").
 * Activé par @Import(JwtSecurityConfig.class).
 */
@Configuration
@EnableMethodSecurity
public class JwtSecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            @Value("${app.security.public-paths:}") String[] publicPaths,
                                            @Value("${app.security.internal-secret:}") String internalSecret) throws Exception {
        http.csrf(c -> c.disable())
                .addFilterBefore(new InternalApiFilter(internalSecret), BearerTokenAuthenticationFilter.class)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> {
                    // /error : sinon un 400/500 sur une route publique est masqué en 401
                    a.requestMatchers("/actuator/health/**", "/error").permitAll();
                    if (publicPaths.length > 0) a.requestMatchers(publicPaths).permitAll();
                    a.anyRequest().authenticated();
                })
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(authenticationConverter())));
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${app.security.jwt-secret}") String secret) {
        return NimbusJwtDecoder.withSecretKey(secretKey(secret)).build();
    }

    /** Partagé avec l'encodeur d'auth-service pour garantir la même clé. */
    public static SecretKey secretKey(String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) throw new IllegalStateException("JWT_SECRET doit faire au moins 32 octets");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    static JwtAuthenticationConverter authenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<GrantedAuthority> authorities = new ArrayList<>();
            List<String> permissions = jwt.getClaimAsStringList("permissions");
            if (permissions != null) permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
            String role = jwt.getClaimAsString("role");
            if (role != null) authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
            return authorities;
        });
        return converter;
    }
}
