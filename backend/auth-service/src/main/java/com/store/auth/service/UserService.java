package com.store.auth.service;

import com.store.auth.dto.Dtos.CreateUserRequest;
import com.store.auth.dto.Dtos.UpdateUserRequest;
import com.store.auth.entity.AppUser;
import com.store.auth.entity.Permission;
import com.store.auth.entity.Role;
import com.store.auth.repository.AppUserRepository;
import com.store.auth.repository.StoreRepository;
import com.store.common.security.CurrentUser;
import com.store.common.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/** Gestion des comptes du store courant (permission USER_MANAGE). */
@Service
@Transactional
public class UserService {

    private final AppUserRepository users;
    private final StoreRepository stores;
    private final PasswordEncoder passwords;

    public UserService(AppUserRepository users, StoreRepository stores, PasswordEncoder passwords) {
        this.users = users;
        this.stores = stores;
        this.passwords = passwords;
    }

    @Transactional(readOnly = true)
    public List<AppUser> list() {
        return users.findByStoreIdOrderByNameAsc(CurrentUser.get().storeId());
    }

    @Transactional(readOnly = true)
    public AppUser get(long id) {
        return users.findByIdAndStoreId(id, CurrentUser.get().storeId())
                .orElseThrow(() -> ApiException.notFound("Utilisateur"));
    }

    /** Nouveau compte = GUEST avec SALE_CREATE. */
    public AppUser create(CreateUserRequest req) {
        String email = AuthService.normalize(req.email());
        if (users.existsByEmailIgnoreCase(email)) throw AuthService.emailTaken();
        var store = stores.getReferenceById(CurrentUser.get().storeId());
        return users.save(new AppUser(store, req.name().trim(), email, passwords.encode(req.password()), Role.GUEST));
    }

    public AppUser update(long id, UpdateUserRequest req) {
        AppUser user = notAdmin(get(id), "désactiver");
        user.setName(req.name().trim());
        user.setActive(req.active());
        return user;
    }

    /** GUEST ↔ MANAGER uniquement : le store garde un seul ADMIN. */
    public AppUser changeRole(long id, Role role) {
        if (role == Role.ADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Un store n'a qu'un seul ADMIN");
        }
        AppUser user = notAdmin(get(id), "changer le rôle de");
        user.changeRole(role);
        return user;
    }

    /** Les permissions supplémentaires sont réservées aux MANAGER : promouvoir d'abord. */
    public AppUser setPermissions(long id, Set<Permission> permissions) {
        AppUser user = notAdmin(get(id), "modifier les permissions de");
        if (user.getRole() != Role.MANAGER) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_MANAGER",
                    "Promouvoir l'utilisateur en MANAGER avant de lui donner des permissions");
        }
        user.setPermissions(permissions);
        return user;
    }

    private static AppUser notAdmin(AppUser user, String action) {
        if (user.getRole() == Role.ADMIN) {
            throw new ApiException(HttpStatus.CONFLICT, "ADMIN_LOCKED", "Impossible de " + action + " l'administrateur");
        }
        return user;
    }
}
