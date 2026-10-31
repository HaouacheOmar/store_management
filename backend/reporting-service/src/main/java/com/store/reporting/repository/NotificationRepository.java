package com.store.reporting.repository;

import com.store.common.events.Events.StockAlert;
import com.store.reporting.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByStoreIdAndProductIdAndTypeAndResolvedAtIsNull(long storeId, long productId, StockAlert type);

    List<Notification> findByStoreIdAndProductIdAndResolvedAtIsNull(long storeId, long productId);

    List<Notification> findByStoreIdAndResolvedAtIsNullOrderByCreatedAtDesc(long storeId);

    Optional<Notification> findByIdAndStoreId(long id, long storeId);
}
