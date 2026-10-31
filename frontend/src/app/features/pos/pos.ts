import { CurrencyPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged, map, merge, startWith, switchMap } from 'rxjs';
import { apiMessage } from '../../core/api-error';
import { CatalogApi, Product } from '../../core/api/catalog.api';
import { SalesApi } from '../../core/api/sales.api';
import { AuthService } from '../../core/auth/auth.service';

interface CartLine {
  product: Product;
  qty: number;
}

/** Montants en centimes entiers : pas d'erreur d'arrondi flottant sur les totaux affichés. */
const cents = (amount: number) => Math.round(amount * 100);

@Component({
  selector: 'app-pos',
  imports: [ReactiveFormsModule, FormsModule, CurrencyPipe, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule],
  template: `
    <div class="pos">
      <section class="catalog" aria-label="Produits">
        <mat-form-field appearance="outline" subscriptSizing="dynamic" class="search">
          <mat-label>Rechercher ou scanner un code</mat-label>
          <input matInput [formControl]="search" (keydown.enter)="addFirstMatch()" autocomplete="off" />
          <mat-icon matSuffix>qr_code_scanner</mat-icon>
        </mat-form-field>

        <div class="tiles">
          @for (p of products(); track p.id) {
            <button type="button" class="tile" (click)="add(p)" [disabled]="p.stockQty === 0">
              <span class="tile-name">{{ p.name }}</span>
              <span class="tile-price">
                @if (p.discountPercent > 0) {
                  <span class="discount-badge">-{{ p.discountPercent }} %</span>
                }
                {{ p.finalPrice | currency: currency : 'symbol' : '1.2-2' }}
              </span>
              <span class="muted">{{ p.stockQty === 0 ? 'Rupture' : 'Stock : ' + p.stockQty }}</span>
            </button>
          } @empty {
            <p class="empty">Aucun produit trouvé.</p>
          }
        </div>
      </section>

      <section class="cart" aria-label="Panier">
        <h2>Panier</h2>
        @for (line of cart(); track line.product.id) {
          <div class="cart-line">
            <div class="cart-name">
              {{ line.product.name }}
              <div class="muted">{{ line.product.finalPrice | currency: currency : 'symbol' : '1.2-2' }} l'unité</div>
            </div>
            <div class="qty">
              <button mat-icon-button (click)="changeQty(line, -1)" [attr.aria-label]="'Retirer un ' + line.product.name">
                <mat-icon>remove</mat-icon>
              </button>
              <span>{{ line.qty }}</span>
              <button mat-icon-button (click)="changeQty(line, 1)" [disabled]="line.qty >= line.product.stockQty"
                      [attr.aria-label]="'Ajouter un ' + line.product.name"><mat-icon>add</mat-icon></button>
            </div>
            <strong class="cart-total">{{ lineTotal(line) / 100 | currency: currency : 'symbol' : '1.2-2' }}</strong>
          </div>
        } @empty {
          <p class="empty">Touchez un produit pour l'ajouter.</p>
        }

        <div class="totals">
          <div class="grand-total"><span>Total</span><span>{{ totalCents() / 100 | currency: currency : 'symbol' : '1.2-2' }}</span></div>
          <mat-form-field appearance="outline" subscriptSizing="dynamic">
            <mat-label>Montant reçu (espèces)</mat-label>
            <input matInput type="number" min="0" step="0.01" [ngModel]="cash()" (ngModelChange)="cash.set($event)"
                   (keydown.enter)="checkout()" />
            <span matTextSuffix>{{ currency }}</span>
          </mat-form-field>
          <div class="change" [class.short]="changeCents() < 0">
            <span>{{ changeCents() < 0 ? 'Manque' : 'À rendre' }}</span>
            <span>{{ (changeCents() < 0 ? -changeCents() : changeCents()) / 100 | currency: currency : 'symbol' : '1.2-2' }}</span>
          </div>
          <button mat-flat-button class="checkout" (click)="checkout()"
                  [disabled]="cart().length === 0 || changeCents() < 0 || saving()">
            <mat-icon>point_of_sale</mat-icon>Encaisser
          </button>
          @if (cart().length) {
            <button mat-button (click)="clear()">Vider le panier</button>
          }
        </div>
      </section>
    </div>
  `,
  styles: `
    .pos { display: grid; grid-template-columns: 1fr 380px; gap: 24px; align-items: start; }
    @media (max-width: 900px) { .pos { grid-template-columns: 1fr; } }
    .search { width: 100%; margin-bottom: 16px; }
    .tiles { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 12px; }
    .tile {
      display: flex; flex-direction: column; gap: 6px; align-items: flex-start; text-align: left;
      min-height: 96px; padding: 12px; cursor: pointer; font: inherit; color: inherit;
      border: 1px solid var(--mat-sys-outline-variant); border-radius: 12px; background: var(--mat-sys-surface);
      &:hover:not(:disabled) { background: var(--mat-sys-surface-container); }
      &:disabled { opacity: .5; cursor: not-allowed; }
    }
    .tile-name { font: var(--mat-sys-title-small); }
    .tile-price { font-weight: 600; }
    .cart {
      position: sticky; top: 16px; padding: 16px; border-radius: 16px;
      background: var(--mat-sys-surface-container-low); border: 1px solid var(--mat-sys-outline-variant);
      h2 { font: var(--mat-sys-title-large); margin: 0 0 8px; }
    }
    .cart-line { display: grid; grid-template-columns: 1fr auto auto; gap: 8px; align-items: center; padding: 8px 0;
      border-bottom: 1px solid var(--mat-sys-outline-variant); }
    .qty { display: flex; align-items: center; font-variant-numeric: tabular-nums; }
    .cart-total { min-width: 90px; text-align: right; font-variant-numeric: tabular-nums; }
    .totals { display: flex; flex-direction: column; gap: 12px; margin-top: 16px; }
    .grand-total, .change { display: flex; justify-content: space-between; font-variant-numeric: tabular-nums; }
    .grand-total { font: var(--mat-sys-headline-small); }
    .change { font: var(--mat-sys-title-medium); &.short { color: var(--mat-sys-error); } }
    .checkout { height: 52px; font-size: 1.1rem; }
  `,
})
export class Pos {
  private readonly catalog = inject(CatalogApi);
  private readonly salesApi = inject(SalesApi);
  private readonly snack = inject(MatSnackBar);
  private readonly router = inject(Router);
  protected readonly currency = inject(AuthService).session()?.currency ?? 'DZD';

  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly products = signal<Product[]>([]);
  protected readonly cart = signal<CartLine[]>([]);
  protected readonly cash = signal<number | null>(null);
  protected readonly saving = signal(false);
  private readonly reload = new Subject<void>();

  protected readonly totalCents = computed(() => this.cart().reduce((sum, l) => sum + this.lineTotal(l), 0));
  protected readonly changeCents = computed(() => cents(this.cash() ?? 0) - this.totalCents());

  constructor() {
    merge(
      this.search.valueChanges.pipe(startWith(''), debounceTime(200), distinctUntilChanged()),
      this.reload.pipe(map(() => this.search.value)),
    )
      .pipe(
        switchMap((q) => this.catalog.products({ q, categoryId: null, stock: 'ALL', page: 0, size: 24 })),
        takeUntilDestroyed(),
      )
      .subscribe((page) => this.products.set(page.content));
  }

  protected lineTotal(line: CartLine): number {
    return cents(line.product.finalPrice) * line.qty;
  }

  add(product: Product): void {
    const existing = this.cart().find((l) => l.product.id === product.id);
    if (existing) {
      this.changeQty(existing, 1);
    } else {
      this.cart.update((lines) => [...lines, { product, qty: 1 }]);
    }
  }

  /** Entrée dans la recherche (douchette) : code exact, ou résultat unique. */
  addFirstMatch(): void {
    const q = this.search.value.trim().toLowerCase();
    const match = this.products().find((p) => p.code?.toLowerCase() === q) ??
      (this.products().length === 1 ? this.products()[0] : undefined);
    if (match && match.stockQty > 0) {
      this.add(match);
      this.search.setValue('');
    }
  }

  changeQty(line: CartLine, delta: number): void {
    const qty = Math.min(line.qty + delta, line.product.stockQty);
    this.cart.update((lines) =>
      qty <= 0 ? lines.filter((l) => l !== line) : lines.map((l) => (l === line ? { ...l, qty } : l)),
    );
  }

  clear(): void {
    this.cart.set([]);
    this.cash.set(null);
  }

  checkout(): void {
    if (this.cart().length === 0 || this.changeCents() < 0 || this.saving()) return;
    this.saving.set(true);
    const lines = this.cart().map((l) => ({ productId: l.product.id, qty: l.qty }));
    this.salesApi.create(lines, this.cash() ?? 0).subscribe({
      next: (sale) => {
        this.clear();
        this.saving.set(false);
        this.router.navigate(['/sales', sale.id], { queryParams: { print: 1 } });
      },
      error: (e: HttpErrorResponse) => {
        this.saving.set(false);
        this.snack.open(apiMessage(e), 'OK', { duration: 6000 });
        this.reload.next(); // stock ou prix ont pu changer

      },
    });
  }
}
