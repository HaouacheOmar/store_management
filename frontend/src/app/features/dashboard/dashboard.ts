import { Component, inject } from '@angular/core';
import { MatChipsModule } from '@angular/material/chips';
import { AuthService } from '../../core/auth/auth.service';
import { PERMISSION_LABELS } from '../../core/auth/labels';
import { AdminDashboard } from './admin-dashboard';
import { GuestDashboard } from './guest-dashboard';

/** Tableau de bord : CRM complet avec STATS_VIEW, sinon l'encaissé personnel du jour. */
@Component({
  selector: 'app-dashboard',
  imports: [MatChipsModule, AdminDashboard, GuestDashboard],
  template: `
    @if (session(); as s) {
      <header class="page-header"><h1>Bonjour {{ s.name }}</h1></header>
      @if (stats) {
        <app-admin-dashboard />
      } @else {
        <app-guest-dashboard />
        <section class="card rights">
          <h2>Mes droits</h2>
          <mat-chip-set aria-label="Mes droits">
            @for (p of s.permissions; track p) {
              <mat-chip>{{ permissions[p] ?? p }}</mat-chip>
            }
          </mat-chip-set>
        </section>
      }
    }
  `,
  styles: `.rights { margin-top: 16px; }`,
})
export class Dashboard {
  private readonly auth = inject(AuthService);
  protected readonly session = this.auth.session;
  protected readonly stats = this.auth.can('STATS_VIEW');
  protected readonly permissions = PERMISSION_LABELS;
}
