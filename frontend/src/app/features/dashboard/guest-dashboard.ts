import { CurrencyPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { MeToday, ReportingApi } from '../../core/api/reporting.api';
import { AuthService } from '../../core/auth/auth.service';
import { StoreToday } from './store-today';

/** PRD M9 : le caissier voit ce qu'il a encaissé aujourd'hui, jamais le chiffre d'affaires du store. */
@Component({
  selector: 'app-guest-dashboard',
  imports: [CurrencyPipe, RouterLink, MatButtonModule, MatIconModule, StoreToday],
  template: `
    <section class="card hero-card">
      <span class="muted">J'ai encaissé aujourd'hui</span>
      <strong class="hero">{{ (me()?.cashed ?? 0) | currency: currency : 'symbol' : '1.2-2' }}</strong>
      <span class="muted">{{ me()?.tickets ?? 0 }} ticket{{ (me()?.tickets ?? 0) > 1 ? 's' : '' }}</span>
      <a mat-flat-button routerLink="/pos"><mat-icon>point_of_sale</mat-icon>Ouvrir la caisse</a>
    </section>
    @if (viewAll) {
      <app-store-today />
    }
  `,
  styles: `
    .hero-card { display: flex; flex-direction: column; align-items: flex-start; gap: 8px; margin-bottom: 16px;
      a { margin-top: 8px; } }
  `,
})
export class GuestDashboard {
  private readonly auth = inject(AuthService);
  protected readonly currency = this.auth.session()?.currency ?? 'DZD';
  protected readonly viewAll = this.auth.can('SALES_VIEW_ALL');
  protected readonly me = signal<MeToday | null>(null);

  constructor() {
    inject(ReportingApi)
      .meToday()
      .subscribe((m) => this.me.set(m));
  }
}
