import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

export type SaleStatus = 'COMPLETED' | 'CANCELLED';

export interface SaleLine {
  productId: number;
  productName: string;
  qty: number;
  unitPrice: number;
  discountPercent: number;
  lineTotal: number;
}

/** Ticket complet. */
export interface Sale {
  id: number;
  cashierId: number;
  cashierName: string;
  createdAt: string;
  saleDate: string;
  status: SaleStatus;
  total: number;
  cashReceived: number;
  changeGiven: number;
  cancelledAt: string | null;
  lines: SaleLine[];
}

export interface SaleSummary {
  id: number;
  createdAt: string;
  cashierId: number;
  cashierName: string;
  total: number;
  itemCount: number;
  status: SaleStatus;
}

@Injectable({ providedIn: 'root' })
export class SalesApi {
  private readonly http = inject(HttpClient);

  /** Prix et remises sont fixés par le serveur : on n'envoie que les produits, quantités et le montant reçu. */
  create(lines: { productId: number; qty: number }[], cashReceived: number) {
    return this.http.post<Sale>('/api/sales/sales', { lines, cashReceived });
  }

  /** date 'YYYY-MM-DD' (jour du store) ; sans SALES_VIEW_ALL, le serveur ne renvoie que ses propres ventes. */
  list(date: string) {
    return this.http.get<SaleSummary[]>('/api/sales/sales', { params: date ? { date } : {} });
  }

  get(id: number) {
    return this.http.get<Sale>(`/api/sales/sales/${id}`);
  }

  cancel(id: number) {
    return this.http.post<Sale>(`/api/sales/sales/${id}/cancel`, {});
  }
}
