import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

export type RankBy = 'QTY' | 'REVENUE';

export interface MeToday {
  cashed: number;
  tickets: number;
}

export interface Ranked {
  id: number;
  name: string;
  qty: number;
  revenue: number;
}

export interface CashierTotal {
  cashierId: number;
  name: string;
  tickets: number;
  total: number;
}

export interface Today {
  cashed: number;
  tickets: number;
  items: number;
  cashiers: CashierTotal[];
  products: Ranked[];
}

export interface Revenue {
  from: string;
  to: string;
  revenue: number;
  tickets: number;
  averageBasket: number;
  itemsSold: number;
  series: { date: string; revenue: number; tickets: number }[];
}

/** Période en jours du store, bornes incluses ('YYYY-MM-DD'). */
export interface Period {
  from: string;
  to: string;
}

@Injectable({ providedIn: 'root' })
export class ReportingApi {
  private readonly http = inject(HttpClient);

  meToday() {
    return this.http.get<MeToday>('/api/reporting/me/today');
  }

  today() {
    return this.http.get<Today>('/api/reporting/today');
  }

  revenue(period: Period) {
    return this.http.get<Revenue>('/api/reporting/revenue', { params: { ...period } });
  }

  topProducts(period: Period, limit: number, by: RankBy, categoryId: number | null) {
    const params: Record<string, string | number> = { ...period, limit, by };
    if (categoryId !== null) params['categoryId'] = categoryId;
    return this.http.get<Ranked[]>('/api/reporting/top-products', { params });
  }

  topCategories(period: Period, limit: number, by: RankBy) {
    return this.http.get<Ranked[]>('/api/reporting/top-categories', { params: { ...period, limit, by } });
  }
}
