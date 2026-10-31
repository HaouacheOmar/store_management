package com.store.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * /internal/** = routes service → service. En plus du JWT de l'utilisateur, l'appelant doit prouver
 * qu'il est un service de la plateforme (secret partagé INTERNAL_SECRET) : un caissier qui joindrait
 * directement un service avec son propre token est refusé. Sans secret configuré, tout est refusé.
 */
public class InternalApiFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";

    private final byte[] secret;

    public InternalApiFilter(String secret) {
        this.secret = secret == null || secret.length() < 32 ? null : secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = request.getHeader(HEADER);
        // comparaison en temps constant : pas d'indice sur le secret via le temps de réponse
        if (secret != null && token != null && MessageDigest.isEqual(secret, token.getBytes(StandardCharsets.UTF_8))) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"Appel interne non autorisé\",\"details\":{}}");
    }
}
