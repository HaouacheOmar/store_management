package com.store.common.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDate;
import java.time.ZoneId;

/** Utilisateur de la requête, lu du JWT. storeId ne vient jamais du client. */
public record CurrentUser(long userId, long storeId, String storeName, String name, String role, ZoneId zone) {

    public static CurrentUser get() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("Aucun utilisateur authentifié");
        }
        return from(jwt);
    }

    public static CurrentUser from(Jwt jwt) {
        return new CurrentUser(
                Long.parseLong(jwt.getSubject()),
                ((Number) jwt.getClaim("storeId")).longValue(),
                jwt.getClaimAsString("storeName"),
                jwt.getClaimAsString("name"),
                jwt.getClaimAsString("role"),
                ZoneId.of(jwt.getClaimAsString("tz")));
    }

    /** « Aujourd'hui » dans le fuseau du store. */
    public LocalDate today() {
        return LocalDate.now(zone);
    }
}
