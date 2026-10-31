package com.store.common.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Instant;
import java.util.List;

/** JWT de test au format d'auth-service. Partagé via le test-jar de common. */
public final class TestTokens {

    public static final String SECRET = "un-secret-de-test-suffisamment-long-32o";

    private TestTokens() {
    }

    public static String token(long userId, long storeId, String role, String... permissions) {
        return token(SECRET, userId, storeId, role, permissions);
    }

    public static String token(String secret, long userId, long storeId, String role, String... permissions) {
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(JwtSecurityConfig.secretKey(secret)));
        var claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId)).expiresAt(Instant.now().plusSeconds(300))
                .claim("storeId", storeId).claim("storeName", "Store " + storeId).claim("name", "User " + userId)
                .claim("tz", "Africa/Algiers").claim("currency", "DZD").claim("role", role)
                .claim("permissions", List.of(permissions))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
