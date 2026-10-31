import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { ROLE_LABELS } from '../auth/labels';

@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatSidenavModule,
    MatToolbarModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
  ],
  template: `
    <mat-sidenav-container class="shell">
      <mat-sidenav #sidenav [mode]="mobile() ? 'over' : 'side'" [opened]="!mobile()">
        <div class="brand">{{ session()?.storeName }}</div>
        <mat-nav-list>
          @for (item of nav; track item.path) {
            <a mat-list-item [routerLink]="item.path" routerLinkActive #rla="routerLinkActive"
               [activated]="rla.isActive" (click)="mobile() && sidenav.close()">
              <mat-icon matListItemIcon>{{ item.icon }}</mat-icon>
              <span matListItemTitle>{{ item.label }}</span>
            </a>
          }
        </mat-nav-list>
      </mat-sidenav>

      <mat-sidenav-content>
        <mat-toolbar>
          @if (mobile()) {
            <button mat-icon-button (click)="sidenav.toggle()" aria-label="Ouvrir le menu">
              <mat-icon>menu</mat-icon>
            </button>
          }
          <span class="spacer"></span>
          <span class="user">{{ session()?.name }} · {{ role() }}</span>
          <button mat-icon-button (click)="auth.logout()" aria-label="Se déconnecter">
            <mat-icon>logout</mat-icon>
          </button>
        </mat-toolbar>
        <main class="content"><router-outlet /></main>
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: `
    .shell { height: 100vh; }
    mat-sidenav { width: 240px; }
    .brand { padding: 20px 16px; font: var(--mat-sys-title-medium); }
    .spacer { flex: 1; }
    .user { font: var(--mat-sys-body-medium); margin-right: 8px; }
    .content { padding: 24px 16px; max-width: 1200px; margin: 0 auto; }
  `,
})
export class Shell {
  protected readonly nav = [
    { path: '/dashboard', icon: 'dashboard', label: 'Tableau de bord' },
    { path: '/products', icon: 'inventory_2', label: 'Produits' },
    { path: '/categories', icon: 'category', label: 'Catégories' },
    { path: '/suppliers', icon: 'local_shipping', label: 'Fournisseurs' },
  ];
  protected readonly auth = inject(AuthService);
  protected readonly session = this.auth.session;
  protected readonly role = computed(() => {
    const s = this.session();
    return s ? ROLE_LABELS[s.role] : '';
  });
  protected readonly mobile = toSignal(
    inject(BreakpointObserver).observe('(max-width: 768px)').pipe(map((r) => r.matches)),
    { initialValue: false },
  );
}
