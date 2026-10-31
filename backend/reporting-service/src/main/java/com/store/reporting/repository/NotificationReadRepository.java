package com.store.reporting.repository;

import com.store.reporting.entity.NotificationRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.Set;

public interface NotificationReadRepository extends JpaRepository<NotificationRead, NotificationRead.Key> {

    @Query("select r.id.notificationId from NotificationRead r where r.id.userId = :userId and r.id.notificationId in :ids")
    Set<Long> readAmong(long userId, Collection<Long> ids);
}
