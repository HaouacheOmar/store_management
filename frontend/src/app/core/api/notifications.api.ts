import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

/** Alerte « à restocker » ouverte ; read = lue par l'utilisateur courant. */
export interface StockNotification {
  id: number;
  productId: number;
  productName: string;
  type: 'LOW' | 'OUT';
  stockQty: number;
  createdAt: string;
  read: boolean;
}

@Injectable({ providedIn: 'root' })
export class NotificationsApi {
  private readonly http = inject(HttpClient);

  list() {
    return this.http.get<StockNotification[]>('/api/reporting/notifications');
  }

  markRead(id: number) {
    return this.http.put<void>(`/api/reporting/notifications/${id}/read`, {});
  }

  markAllRead() {
    return this.http.put<void>('/api/reporting/notifications/read-all', {});
  }
}
