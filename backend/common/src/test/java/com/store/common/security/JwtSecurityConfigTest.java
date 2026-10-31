package com.store.common.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtSecurityConfigTest {

    static final String SECRET = "un-secret-de-test-suffisamment-long-32o";

    static String token(String secret) {
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(JwtSecurityConfig.secretKey(secret)));
        var claims = JwtClaimsSet.builder()
                .subject("7").expiresAt(Instant.now().plusSeconds(60))
                .claim("storeId", 42).claim("storeName", "Épicerie").claim("name", "Ali")
                .claim("tz", "Africa/Algiers").claim("role", "MANAGER")
                .claim("permissions", List.of("SALE_CREATE", "STOCK_RESTOCK"))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    @Test
    void tokenSigneAvecLeBonSecret_donneUtilisateurEtPermissions() {
        Jwt jwt = new JwtSecurityConfig().jwtDecoder(SECRET).decode(token(SECRET));

        var user = CurrentUser.from(jwt);
        assertThat(user.userId()).isEqualTo(7);
        assertThat(user.storeId()).isEqualTo(42);

        var authorities = JwtSecurityConfig.authenticationConverter().convert(jwt).getAuthorities()
                .stream().map(GrantedAuthority::getAuthority).toList();
        assertThat(authorities).containsExactlyInAnyOrder("SALE_CREATE", "STOCK_RESTOCK", "ROLE_MANAGER");
    }

    @Test
    void tokenSigneAvecUnAutreSecret_estRefuse() {
        var decoder = new JwtSecurityConfig().jwtDecoder(SECRET);
        assertThatThrownBy(() -> decoder.decode(token("un-autre-secret-tout-aussi-long-32-octets")))
                .isInstanceOf(JwtException.class);
    }
}
