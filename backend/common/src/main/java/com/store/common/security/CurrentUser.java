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

    /** Pour les règles de visibilité (ex. SALES_VIEW_ALL) ; les autorisations d'accès restent sur @PreAuthorize. */
    public static boolean has(String authority) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(authority));
    }

    /** « Aujourd'hui » dans le fuseau du store. */
    public LocalDate today() {
        return LocalDate.now(zone);
    }
}
