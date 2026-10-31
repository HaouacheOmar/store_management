package com.store.auth.controller;

import com.store.auth.dto.Dtos.LoginRequest;
import com.store.auth.dto.Dtos.RegisterRequest;
import com.store.auth.dto.Dtos.TokenResponse;
import com.store.auth.dto.Dtos.UserResponse;
import com.store.auth.repository.AppUserRepository;
import com.store.auth.service.AuthService;
import com.store.common.security.CurrentUser;
import com.store.common.web.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService auth;
    private final AppUserRepository users;

    public AuthController(AuthService auth, AppUserRepository users) {
        this.auth = auth;
        this.users = users;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody RegisterRequest req) {
        return new TokenResponse(auth.register(req));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        return new TokenResponse(auth.login(req));
    }

    @GetMapping("/me")
    public UserResponse me() {
        var me = CurrentUser.get();
        return users.findByIdAndStoreId(me.userId(), me.storeId()).map(UserResponse::of)
                .orElseThrow(() -> ApiException.notFound("Utilisateur"));
    }
}
