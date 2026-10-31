package com.store.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtSecurityConfigTest {

    @Test
    void tokenSigneAvecLeBonSecret_donneUtilisateurEtPermissions() {
        String token = TestTokens.token(7, 42, "MANAGER", "SALE_CREATE", "STOCK_RESTOCK");
        Jwt jwt = new JwtSecurityConfig().jwtDecoder(TestTokens.SECRET).decode(token);

        var user = CurrentUser.from(jwt);
        assertThat(user.userId()).isEqualTo(7);
        assertThat(user.storeId()).isEqualTo(42);

        var authorities = JwtSecurityConfig.authenticationConverter().convert(jwt).getAuthorities()
                .stream().map(GrantedAuthority::getAuthority).toList();
        assertThat(authorities).containsExactlyInAnyOrder("SALE_CREATE", "STOCK_RESTOCK", "ROLE_MANAGER");
    }

    @Test
    void tokenSigneAvecUnAutreSecret_estRefuse() {
        var decoder = new JwtSecurityConfig().jwtDecoder(TestTokens.SECRET);
        String forged = TestTokens.token("un-autre-secret-tout-aussi-long-32-octets", 7, 42, "ADMIN");
        assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
    }
}
