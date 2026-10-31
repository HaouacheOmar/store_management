package com.store.auth.dto;

import com.store.auth.entity.AppUser;
import com.store.auth.entity.Permission;
import com.store.auth.entity.Role;
import jakarta.validation.constraints.*;

import java.util.Set;

public final class Dtos {

    private Dtos() {
    }

    /** timezone : envoyé par le navigateur ; currency : code ISO 4217. Tous deux optionnels. */
    public record RegisterRequest(@NotBlank @Size(max = 120) String storeName,
                                  @NotBlank @Size(max = 120) String name,
                                  @NotBlank @Email @Size(max = 180) String email,
                                  @NotBlank @Size(min = 8, max = 72) String password,
                                  String timezone,
                                  @Pattern(regexp = "[A-Z]{3}") String currency) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record TokenResponse(String token) {
    }

    public record CreateUserRequest(@NotBlank @Size(max = 120) String name,
                                    @NotBlank @Email @Size(max = 180) String email,
                                    @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record UpdateUserRequest(@NotBlank @Size(max = 120) String name, boolean active) {
    }

    public record RoleRequest(@NotNull Role role) {
    }

    public record PermissionsRequest(@NotNull Set<Permission> permissions) {
    }

    public record UserResponse(long id, String name, String email, Role role, boolean active,
                               Set<Permission> permissions) {
        public static UserResponse of(AppUser u) {
            return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole(), u.isActive(),
                    u.effectivePermissions());
        }
    }
}
