import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, computed, DestroyRef, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';
import { CatalogApi } from '../api/catalog.api';
import { StockNotification } from '../api/notifications.api';
import { AuthService } from '../auth/auth.service';
import { ROLE_LABELS } from '../auth/labels';
import { NotificationCenter } from '../notifications/notification-center';
import { RestockDialog } from '../../features/stock/restock-dialog';

interface NavItem {
  path: string;
  icon: string;
  label: string;
  /** Lien masqué sans cette permission (l'API et la route la vérifient aussi). */
  permission?: string;
}

const NAV: NavItem[] = [
  { path: '/dashboard', icon: 'dashboard', label: 'Tableau de bord' },
  { path: '/pos', icon: 'point_of_sale', label: 'Caisse' },
  { path: '/sales', icon: 'receipt_long', label: 'Ventes' },
  { path: '/products', icon: 'inventory_2', label: 'Produits' },
  { path: '/categories', icon: 'category', label: 'Catégories' },
  { path: '/restocks', icon: 'move_to_inbox', label: 'Restockages' },
  { path: '/discounts', icon: 'sell', label: 'Remises' },
  { path: '/suppliers', icon: 'local_shipping', label: 'Fournisseurs' },
  { path: '/users', icon: 'group', label: 'Utilisateurs', permission: 'USER_MANAGE' },
];

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
    MatBadgeModule,
    MatMenuModule,
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
          @if (canRestock) {
            <button mat-icon-button [matMenuTriggerFor]="bell"
                    [attr.aria-label]="'Notifications, ' + center.unread() + ' non lue(s)'">
              <mat-icon [matBadge]="center.unread() || null" matBadgeColor="warn"
                        aria-hidden="true">notifications</mat-icon>
            </button>
            <mat-menu #bell="matMenu" class="bell-menu" xPosition="before">
              <div class="bell-head" (click)="$event.stopPropagation()" (keydown)="$event.stopPropagation()">
                <strong>À restocker</strong>
                @if (center.unread()) {
                  <button mat-button (click)="center.markAllRead()">Tout marquer comme lu</button>
                }
              </div>
              @for (n of center.items(); track n.id) {
                <button mat-menu-item (click)="openRestock(n)" [class.unread]="!n.read">
                  <mat-icon [class.out]="n.type === 'OUT'">{{ n.type === 'OUT' ? 'error' : 'warning' }}</mat-icon>
                  <span>{{ n.productName }} · {{ n.type === 'OUT' ? 'rupture' : 'stock bas (' + n.stockQty + ')' }}</span>
                </button>
              } @empty {
                <p class="bell-empty">Rien à restocker.</p>
              }
            </mat-menu>
          }
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
    .user { font: var(--mat-sys-body-medium); margin: 0 8px; }
    .content { padding: 24px 16px; max-width: 1200px; margin: 0 auto; }
  `,
})
export class Shell {
  protected readonly auth = inject(AuthService);
  protected readonly center = inject(NotificationCenter);
  private readonly catalog = inject(CatalogApi);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);

  protected readonly nav = NAV.filter((item) => !item.permission || this.auth.can(item.permission));
  protected readonly canRestock = this.auth.can('STOCK_RESTOCK');
  protected readonly session = this.auth.session;
  protected readonly role = computed(() => {
    const s = this.session();
    return s ? ROLE_LABELS[s.role] : '';
  });
  protected readonly mobile = toSignal(
    inject(BreakpointObserver).observe('(max-width: 768px)').pipe(map((r) => r.matches)),
    { initialValue: false },
  );

  constructor() {
    this.center.start();
    // le shell disparaît à la déconnexion : on coupe le WebSocket
    inject(DestroyRef).onDestroy(() => this.center.stop());
  }

  /** US-10.3 : depuis l'alerte, on ouvre directement le restockage du produit. */
  openRestock(notification: StockNotification): void {
    this.center.markRead(notification);
    this.catalog.product(notification.productId).subscribe((product) =>
      this.dialog
        .open(RestockDialog, { data: product, width: '480px' })
        .afterClosed()
        .subscribe((done) => done && this.snack.open(`${product.name} restocké`, undefined, { duration: 2500 })),
    );
  }
}
