import { CurrencyPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { apiMessage } from '../../core/api-error';
import { CatalogApi, Category, Product, StockFilter } from '../../core/api/catalog.api';
import { AuthService } from '../../core/auth/auth.service';
import { confirmDialog } from '../../shared/confirm-dialog';
import { frenchPaginator } from '../../shared/french-paginator';
import { RestockDialog } from '../stock/restock-dialog';
import { ProductDialog } from './product-dialog';

@Component({
  selector: 'app-products',
  imports: [
    ReactiveFormsModule,
    CurrencyPipe,
    MatTableModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    MatTooltipModule,
  ],
  providers: [{ provide: MatPaginatorIntl, useFactory: frenchPaginator }],
  template: `
    <header class="page-header">
      <h1>Produits</h1>
      @if (canWrite) {
        <button mat-flat-button (click)="edit(null)"><mat-icon>add</mat-icon>Nouveau produit</button>
      }
    </header>

    <div class="filters">
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>Rechercher</mat-label>
        <input matInput [formControl]="search" placeholder="Nom ou code" />
        <mat-icon matSuffix>search</mat-icon>
      </mat-form-field>
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>Catégorie</mat-label>
        <mat-select [value]="categoryId()" (selectionChange)="categoryId.set($event.value); load(0)">
          <mat-option [value]="null">Toutes</mat-option>
          @for (c of categories(); track c.id) {
            <mat-option [value]="c.id">{{ c.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-button-toggle-group [value]="stock()" (change)="stock.set($event.value); load(0)" aria-label="Filtre de stock">
        <mat-button-toggle value="ALL">Tous</mat-button-toggle>
        <mat-button-toggle value="LOW">À restocker</mat-button-toggle>
        <mat-button-toggle value="OUT">En rupture</mat-button-toggle>
      </mat-button-toggle-group>
    </div>

    <div class="table-wrap">
      <table mat-table [dataSource]="rows()">
        <ng-container matColumnDef="name">
          <th mat-header-cell *matHeaderCellDef>Produit</th>
          <td mat-cell *matCellDef="let p">
            {{ p.name }}
            @if (p.code) {
              <div class="muted">{{ p.code }}</div>
            }
          </td>
        </ng-container>
        <ng-container matColumnDef="category">
          <th mat-header-cell *matHeaderCellDef>Catégorie</th>
          <td mat-cell *matCellDef="let p">{{ p.categoryName }}</td>
        </ng-container>
        <ng-container matColumnDef="price">
          <th mat-header-cell *matHeaderCellDef class="num">Prix</th>
          <td mat-cell *matCellDef="let p" class="num">
            @if (p.discountPercent > 0) {
              <span class="discount-badge">-{{ p.discountPercent }} %</span>
              <s class="muted">{{ p.price | currency: currency : 'symbol' : '1.2-2' }}</s>
              <div><strong>{{ p.finalPrice | currency: currency : 'symbol' : '1.2-2' }}</strong></div>
            } @else {
              {{ p.price | currency: currency : 'symbol' : '1.2-2' }}
            }
          </td>
        </ng-container>
        <ng-container matColumnDef="stock">
          <th mat-header-cell *matHeaderCellDef class="num">Stock</th>
          <td mat-cell *matCellDef="let p" class="num">
            <span class="stock" [class.out]="p.stockQty === 0" [class.low]="p.stockQty > 0 && p.stockQty <= p.alertThreshold"
                  [matTooltip]="'Seuil d\\'alerte : ' + p.alertThreshold">{{ p.stockQty }}</span>
          </td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let p" class="actions">
            @if (canRestock) {
              <button mat-icon-button (click)="restock(p)" [attr.aria-label]="'Restocker ' + p.name" matTooltip="Restocker">
                <mat-icon>add_box</mat-icon>
              </button>
            }
            @if (canWrite) {
              <button mat-icon-button (click)="edit(p)" [attr.aria-label]="'Modifier ' + p.name"><mat-icon>edit</mat-icon></button>
              <button mat-icon-button (click)="remove(p)" [attr.aria-label]="'Supprimer ' + p.name"><mat-icon>delete</mat-icon></button>
            }
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
      @if (!loading() && rows().length === 0) {
        <p class="empty">Aucun produit{{ filtered() ? ' ne correspond aux filtres' : '' }}.</p>
      }
    </div>
    <mat-paginator [length]="total()" [pageIndex]="pageIndex()" [pageSize]="pageSize()"
                   [pageSizeOptions]="[20, 50, 100]" (page)="onPage($event)" />
  `,
})
export class Products {
  private readonly api = inject(CatalogApi);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  private readonly auth = inject(AuthService);

  protected readonly canWrite = this.auth.can('PRODUCT_WRITE');
  protected readonly canRestock = this.auth.can('STOCK_RESTOCK');
  protected readonly currency = this.auth.session()?.currency ?? 'DZD';
  protected readonly columns = ['name', 'category', 'price', 'stock'].concat(
    this.canWrite || this.canRestock ? ['actions'] : [],
  );

  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly categoryId = signal<number | null>(null);
  protected readonly stock = signal<StockFilter>('ALL');
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(20);

  protected readonly categories = signal<Category[]>([]);
  protected readonly rows = signal<Product[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(true);
  protected readonly filtered = computed(
    () => this.categoryId() !== null || this.stock() !== 'ALL' || this.search.value.trim() !== '',
  );

  constructor() {
    this.api.categories().subscribe((c) => this.categories.set(c));
    this.search.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => this.load(0));
    this.load(0);
  }

  load(page = this.pageIndex()): void {
    this.loading.set(true);
    this.pageIndex.set(page);
    this.api
      .products({
        q: this.search.value,
        categoryId: this.categoryId(),
        stock: this.stock(),
        page,
        size: this.pageSize(),
      })
      .subscribe({
        next: (result) => {
          this.rows.set(result.content);
          this.total.set(result.page.totalElements);
          this.loading.set(false);
        },
        error: (e: HttpErrorResponse) => this.fail(e),
      });
  }

  onPage(e: PageEvent): void {
    this.pageSize.set(e.pageSize);
    this.load(e.pageIndex);
  }

  edit(product: Product | null): void {
    if (this.categories().length === 0) {
      this.snack.open("Créez d'abord une catégorie", 'OK', { duration: 4000 });
      return;
    }
    this.dialog
      .open(ProductDialog, {
        data: { product, categories: this.categories(), currency: this.currency },
        width: '560px',
      })
      .afterClosed()
      .subscribe((saved) => {
        if (!saved) return;
        this.snack.open(product ? 'Produit modifié' : 'Produit ajouté', undefined, { duration: 2500 });
        this.load();
      });
  }

  restock(product: Product): void {
    this.dialog
      .open(RestockDialog, { data: product, width: '480px' })
      .afterClosed()
      .subscribe((done) => {
        if (!done) return;
        this.snack.open(`${product.name} restocké`, undefined, { duration: 2500 });
        this.load();
      });
  }

  remove(product: Product): void {
    confirmDialog(this.dialog, {
      title: 'Supprimer le produit',
      message: `« ${product.name} » ne sera plus vendable. Son historique de ventes est conservé.`,
      action: 'Supprimer',
    }).subscribe((ok) => {
      if (!ok) return;
      this.api.deleteProduct(product.id).subscribe({
        next: () => {
          this.snack.open('Produit supprimé', undefined, { duration: 2500 });
          this.load();
        },
        error: (e: HttpErrorResponse) => this.fail(e),
      });
    });
  }

  private fail(e: HttpErrorResponse): void {
    this.loading.set(false);
    this.snack.open(apiMessage(e), 'OK', { duration: 5000 });
  }
}
