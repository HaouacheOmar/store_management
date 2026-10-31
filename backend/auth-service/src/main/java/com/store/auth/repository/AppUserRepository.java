package com.store.auth.repository;

import com.store.auth.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<AppUser> findByStoreIdOrderByNameAsc(long storeId);

    /** Toute lecture par id passe par le store : un id d'un autre store donne NOT_FOUND. */
    Optional<AppUser> findByIdAndStoreId(long id, long storeId);
}
