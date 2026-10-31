package com.store.auth.controller;

import com.store.auth.dto.Dtos.*;
import com.store.auth.entity.Permission;
import com.store.auth.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/auth")
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class UserController {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @GetMapping("/permissions")
    public Permission[] permissions() {
        return Permission.values();
    }

    @GetMapping("/users")
    public List<UserResponse> list() {
        return users.list().stream().map(UserResponse::of).toList();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest req) {
        return UserResponse.of(users.create(req));
    }

    @PutMapping("/users/{id}")
    public UserResponse update(@PathVariable long id, @Valid @RequestBody UpdateUserRequest req) {
        return UserResponse.of(users.update(id, req));
    }

    @PutMapping("/users/{id}/role")
    public UserResponse role(@PathVariable long id, @Valid @RequestBody RoleRequest req) {
        return UserResponse.of(users.changeRole(id, req.role()));
    }

    @PutMapping("/users/{id}/permissions")
    public UserResponse permissions(@PathVariable long id, @Valid @RequestBody PermissionsRequest req) {
        return UserResponse.of(users.setPermissions(id, req.permissions()));
    }
}
