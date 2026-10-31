package com.store.reporting.websocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

/**
 * Prévient les navigateurs du store que ses notifications ont changé ; ils relisent l'API REST
 * (seule source de vérité, avec l'état lu propre à chaque utilisateur).
 */
@Component
public class NotificationPush {

    private final SimpMessagingTemplate broker;

    public NotificationPush(SimpMessagingTemplate broker) {
        this.broker = broker;
    }

    public static String topic(long storeId) {
        return "/topic/stores/" + storeId + "/notifications";
    }

    /** Après commit : sinon le navigateur relirait avant que la notification soit visible. */
    public void storeChangedAfterCommit(long storeId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                broker.convertAndSend(topic(storeId), Map.of("event", "NOTIFICATIONS_CHANGED"));
            }
        });
    }
}
