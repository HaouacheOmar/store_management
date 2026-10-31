import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute } from '@angular/router';
import { CatalogApi, Restock } from '../../core/api/catalog.api';
import { Supplier, SupplierApi } from '../../core/api/supplier.api';
import { AuthService } from '../../core/auth/auth.service';
import { frenchPaginator } from '../../shared/french-paginator';

/** Historique des restockages (US-3.4), filtrable par fournisseur (US-4.3) et par dates. */
@Component({
  selector: 'app-restocks',
  imports: [
    FormsModule,
    CurrencyPipe,
    DatePipe,
    MatTableModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  providers: [{ provide: MatPaginatorIntl, useFactory: frenchPaginator }],
  template: `
    <header class="page-header"><h1>Restockages</h1></header>

    <div class="filters">
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>Fournisseur</mat-label>
        <mat-select [ngModel]="supplierId()" (ngModelChange)="supplierId.set($event); load(0)">
          <mat-option [value]="null">Tous</mat-option>
          @for (s of suppliers(); track s.id) {
            <mat-option [value]="s.id">{{ s.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>Du</mat-label>
        <input matInput type="date" [ngModel]="from()" (ngModelChange)="from.set($event); load(0)" />
      </mat-form-field>
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>Au</mat-label>
        <input matInput type="date" [ngModel]="to()" (ngModelChange)="to.set($event); load(0)" />
      </mat-form-field>
    </div>

    <div class="table-wrap">
      <table mat-table [dataSource]="rows()">
        <ng-container matColumnDef="date">
          <th mat-header-cell *matHeaderCellDef>Date</th>
          <td mat-cell *matCellDef="let r">{{ r.createdAt | date: 'dd/MM/yyyy HH:mm' }}</td>
        </ng-container>
        <ng-container matColumnDef="product">
          <th mat-header-cell *matHeaderCellDef>Produit</th>
          <td mat-cell *matCellDef="let r">{{ r.productName }}</td>
        </ng-container>
        <ng-container matColumnDef="supplier">
          <th mat-header-cell *matHeaderCellDef>Fournisseur</th>
          <td mat-cell *matCellDef="let r">{{ r.supplierName }}</td>
        </ng-container>
        <ng-container matColumnDef="qty">
          <th mat-header-cell *matHeaderCellDef class="num">Quantité</th>
          <td mat-cell *matCellDef="let r" class="num">+{{ r.qty }}</td>
        </ng-container>
        <ng-container matColumnDef="cost">
          <th mat-header-cell *matHeaderCellDef class="num">Prix d'achat</th>
          <td mat-cell *matCellDef="let r" class="num">
            {{ r.unitCost === null ? '—' : (r.unitCost | currency: currency : 'symbol' : '1.2-2') }}
          </td>
        </ng-container>
        <ng-container matColumnDef="user">
          <th mat-header-cell *matHeaderCellDef>Par</th>
          <td mat-cell *matCellDef="let r">{{ r.userName }}</td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
      @if (loaded() && rows().length === 0) {
        <p class="empty">Aucun restockage sur cette période.</p>
      }
    </div>
    <mat-paginator [length]="total()" [pageIndex]="pageIndex()" [pageSize]="pageSize()"
                   [pageSizeOptions]="[20, 50, 100]" (page)="onPage($event)" />
  `,
})
export class Restocks {
  private readonly api = inject(CatalogApi);
  protected readonly currency = inject(AuthService).session()?.currency ?? 'DZD';
  protected readonly columns = ['date', 'product', 'supplier', 'qty', 'cost', 'user'];

  protected readonly suppliers = signal<Supplier[]>([]);
  // lien depuis la page Fournisseurs : /restocks?supplierId=…
  protected readonly supplierId = signal<number | null>(
    Number(inject(ActivatedRoute).snapshot.queryParamMap.get('supplierId')) || null,
  );
  protected readonly from = signal('');
  protected readonly to = signal('');
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(20);
  protected readonly rows = signal<Restock[]>([]);
  protected readonly total = signal(0);
  protected readonly loaded = signal(false);

  constructor() {
    inject(SupplierApi)
      .list()
      .subscribe((s) => this.suppliers.set(s));
    this.load(0);
  }

  load(page = this.pageIndex()): void {
    this.pageIndex.set(page);
    this.api
      .restocks({ supplierId: this.supplierId(), from: this.from(), to: this.to(), page, size: this.pageSize() })
      .subscribe((result) => {
        this.rows.set(result.content);
        this.total.set(result.page.totalElements);
        this.loaded.set(true);
      });
  }

  onPage(e: PageEvent): void {
    this.pageSize.set(e.pageSize);
    this.load(e.pageIndex);
  }
}
