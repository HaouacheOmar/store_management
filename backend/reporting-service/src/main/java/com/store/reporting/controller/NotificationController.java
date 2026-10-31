package com.store.reporting.controller;

import com.store.common.events.Events.StockAlert;
import com.store.common.security.CurrentUser;
import com.store.common.web.ApiException;
import com.store.reporting.entity.Notification;
import com.store.reporting.entity.NotificationRead;
import com.store.reporting.repository.NotificationReadRepository;
import com.store.reporting.repository.NotificationRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Alertes de stock ouvertes du store, avec l'état lu de l'utilisateur courant (PRD M10). */
@RestController
@RequestMapping("/reporting/notifications")
@PreAuthorize("hasAuthority('STOCK_RESTOCK')")
public class NotificationController {

    public record NotificationView(long id, long productId, String productName, StockAlert type, int stockQty,
                                   Instant createdAt, boolean read) {
    }

    private final NotificationRepository notifications;
    private final NotificationReadRepository reads;

    public NotificationController(NotificationRepository notifications, NotificationReadRepository reads) {
        this.notifications = notifications;
        this.reads = reads;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<NotificationView> list(@RequestParam(defaultValue = "false") boolean unread) {
        var me = CurrentUser.get();
        List<Notification> open = notifications.findByStoreIdAndResolvedAtIsNullOrderByCreatedAtDesc(me.storeId());
        Set<Long> read = open.isEmpty() ? Set.of()
                : reads.readAmong(me.userId(), open.stream().map(Notification::getId).toList());
        return open.stream()
                .map(n -> new NotificationView(n.getId(), n.getProductId(), n.getProductName(), n.getType(),
                        n.getStockQty(), n.getCreatedAt(), read.contains(n.getId())))
                .filter(v -> !unread || !v.read())
                .toList();
    }

    @PutMapping("/{id}/read")
    @Transactional
    public void markRead(@PathVariable long id) {
        var me = CurrentUser.get();
        Notification n = notifications.findByIdAndStoreId(id, me.storeId())
                .orElseThrow(() -> ApiException.notFound("Notification"));
        markRead(n.getId(), me.userId());
    }

    @PutMapping("/read-all")
    @Transactional
    public void markAllRead() {
        var me = CurrentUser.get();
        notifications.findByStoreIdAndResolvedAtIsNullOrderByCreatedAtDesc(me.storeId())
                .forEach(n -> markRead(n.getId(), me.userId()));
    }

    private void markRead(long notificationId, long userId) {
        var key = new NotificationRead.Key(notificationId, userId);
        if (!reads.existsById(key)) reads.save(new NotificationRead(key));
    }
}
