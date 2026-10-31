package com.store.auth.service;

import com.store.auth.dto.Dtos.LoginRequest;
import com.store.auth.dto.Dtos.RegisterRequest;
import com.store.auth.entity.AppUser;
import com.store.auth.entity.Role;
import com.store.auth.entity.Store;
import com.store.auth.repository.AppUserRepository;
import com.store.auth.repository.StoreRepository;
import com.store.common.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;

@Service
public class AuthService {

    private final StoreRepository stores;
    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final JwtEncoder jwtEncoder;
    private final Duration ttl;

    public AuthService(StoreRepository stores, AppUserRepository users, PasswordEncoder passwords,
                       JwtEncoder jwtEncoder, @Value("${app.jwt-ttl}") Duration ttl) {
        this.stores = stores;
        this.users = users;
        this.passwords = passwords;
        this.jwtEncoder = jwtEncoder;
        this.ttl = ttl;
    }

    /** Inscription d'un commerçant : création du store + de son compte ADMIN. */
    @Transactional
    public String register(RegisterRequest req) {
        String email = normalize(req.email());
        if (users.existsByEmailIgnoreCase(email)) throw emailTaken();
        Store store = stores.save(new Store(req.storeName().trim(), validZone(req.timezone()),
                req.currency() == null ? "DZD" : req.currency()));
        AppUser admin = users.save(new AppUser(store, req.name().trim(), email, passwords.encode(req.password()), Role.ADMIN));
        return issueToken(admin);
    }

    @Transactional(readOnly = true)
    public String login(LoginRequest req) {
        AppUser user = users.findByEmailIgnoreCase(normalize(req.email()))
                .filter(u -> passwords.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                        "Email ou mot de passe incorrect"));
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "Ce compte est désactivé");
        }
        return issueToken(user);
    }

    String issueToken(AppUser user) {
        Store store = user.getStore();
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("auth-service")
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("storeId", store.getId())
                .claim("storeName", store.getName())
                .claim("tz", store.getTimezone())
                .claim("currency", store.getCurrency())
                .claim("name", user.getName())
                .claim("role", user.getRole().name())
                .claim("permissions", user.effectivePermissions().stream().map(Enum::name).sorted().toList())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    static ApiException emailTaken() {
        return new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Cet email est déjà utilisé");
    }

    private static String validZone(String timezone) {
        if (timezone == null || timezone.isBlank()) return "Africa/Algiers";
        try {
            return ZoneId.of(timezone).getId();
        } catch (DateTimeException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Fuseau horaire inconnu : " + timezone);
        }
    }
}
