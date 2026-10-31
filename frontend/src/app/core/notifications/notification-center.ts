import { computed, inject, Injectable, signal } from '@angular/core';
import { Client } from '@stomp/stompjs';
import { NotificationsApi, StockNotification } from '../api/notifications.api';
import { AuthService } from '../auth/auth.service';

/**
 * Cloche « à restocker » (PRD M10) : le serveur pousse un signal STOMP quand les alertes du store
 * changent, et on relit l'API REST (seule source de vérité, avec l'état lu propre à l'utilisateur).
 */
@Injectable({ providedIn: 'root' })
export class NotificationCenter {
  private readonly api = inject(NotificationsApi);
  private readonly auth = inject(AuthService);
  private client?: Client;

  readonly items = signal<StockNotification[]>([]);
  readonly unread = computed(() => this.items().filter((n) => !n.read).length);

  /** Réservé aux destinataires des alertes (STOCK_RESTOCK) ; sans effet si déjà démarré. */
  start(): void {
    const session = this.auth.session();
    const token = this.auth.getToken();
    if (this.client || !session || !token || !this.auth.can('STOCK_RESTOCK')) return;

    this.client = new Client({
      brokerURL: `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`,
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
      onConnect: () => {
        this.client?.subscribe(`/topic/stores/${session.storeId}/notifications`, () => this.reload());
        this.reload(); // rattrape ce qui a changé pendant une coupure
      },
    });
    this.client.activate();
  }

  stop(): void {
    void this.client?.deactivate();
    this.client = undefined;
    this.items.set([]);
  }

  reload(): void {
    this.api.list().subscribe((items) => this.items.set(items));
  }

  markRead(notification: StockNotification): void {
    if (notification.read) return;
    this.items.update((items) => items.map((n) => (n.id === notification.id ? { ...n, read: true } : n)));
    this.api.markRead(notification.id).subscribe();
  }

  markAllRead(): void {
    this.items.update((items) => items.map((n) => ({ ...n, read: true })));
    this.api.markAllRead().subscribe();
  }
}
