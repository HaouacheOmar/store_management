package com.store.auth.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "store_id")
    private Store store;

    private String name;
    private String email;
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    private Role role;

    private boolean active = true;

    /** Vide pour un ADMIN : il reçoit toutes les permissions (cf. effectivePermissions). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_permission", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "permission")
    @Enumerated(EnumType.STRING)
    private Set<Permission> permissions = new HashSet<>();

    private Instant createdAt = Instant.now();

    protected AppUser() {
    }

    public AppUser(Store store, String name, String email, String passwordHash, Role role) {
        this.store = store;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        if (role == Role.GUEST) permissions.add(Permission.SALE_CREATE);
    }

    public Set<Permission> effectivePermissions() {
        return role == Role.ADMIN ? EnumSet.allOf(Permission.class) : Set.copyOf(permissions);
    }

    /** Retour à GUEST = retour aux droits par défaut. */
    public void changeRole(Role newRole) {
        role = newRole;
        if (newRole == Role.GUEST) setPermissions(Set.of());
    }

    /** SALE_CREATE fait partie de tout compte non admin. */
    public void setPermissions(Set<Permission> newPermissions) {
        permissions.clear();
        permissions.addAll(newPermissions);
        permissions.add(Permission.SALE_CREATE);
    }

    public Long getId() {
        return id;
    }

    public Store getStore() {
        return store;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
