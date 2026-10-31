import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatTableModule } from '@angular/material/table';
import { Router } from '@angular/router';
import { SaleSummary, SalesApi } from '../../core/api/sales.api';
import { AuthService } from '../../core/auth/auth.service';

/** 'YYYY-MM-DD' local (l'input date et l'API raisonnent en jours, pas en instants UTC). */
function today(): string {
  const d = new Date();
  return new Date(d.getTime() - d.getTimezoneOffset() * 60_000).toISOString().slice(0, 10);
}

/** Ventes du jour (PRD M7). Les ventes annulées restent visibles mais sortent des totaux. */
@Component({
  selector: 'app-sales',
  imports: [FormsModule, CurrencyPipe, DatePipe, MatTableModule, MatFormFieldModule, MatInputModule],
  template: `
    <header class="page-header">
      <h1>Ventes {{ viewAll ? 'du magasin' : 'de ma caisse' }}</h1>
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>Jour</mat-label>
        <input matInput type="date" [ngModel]="date()" (ngModelChange)="date.set($event); load()" [max]="maxDate" />
      </mat-form-field>
    </header>

    <div class="kpis">
      <div class="kpi"><span class="muted">Encaissé</span><strong>{{ cashed() | currency: currency : 'symbol' : '1.2-2' }}</strong></div>
      <div class="kpi"><span class="muted">Tickets</span><strong>{{ completed().length }}</strong></div>
      <div class="kpi"><span class="muted">Articles vendus</span><strong>{{ items() }}</strong></div>
    </div>

    <div class="table-wrap">
      <table mat-table [dataSource]="rows()">
        <ng-container matColumnDef="id">
          <th mat-header-cell *matHeaderCellDef>N°</th>
          <td mat-cell *matCellDef="let s">{{ s.id }}</td>
        </ng-container>
        <ng-container matColumnDef="time">
          <th mat-header-cell *matHeaderCellDef>Heure</th>
          <td mat-cell *matCellDef="let s">{{ s.createdAt | date: 'HH:mm' }}</td>
        </ng-container>
        <ng-container matColumnDef="cashier">
          <th mat-header-cell *matHeaderCellDef>Caissier</th>
          <td mat-cell *matCellDef="let s">{{ s.cashierName }}</td>
        </ng-container>
        <ng-container matColumnDef="items">
          <th mat-header-cell *matHeaderCellDef class="num">Articles</th>
          <td mat-cell *matCellDef="let s" class="num">{{ s.itemCount }}</td>
        </ng-container>
        <ng-container matColumnDef="total">
          <th mat-header-cell *matHeaderCellDef class="num">Total</th>
          <td mat-cell *matCellDef="let s" class="num" [class.struck]="s.status === 'CANCELLED'">
            {{ s.total | currency: currency : 'symbol' : '1.2-2' }}
          </td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let s">
            @if (s.status === 'CANCELLED') {
              <span class="stock out">Annulée</span>
            }
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns" class="clickable" (click)="open(row)"
            tabindex="0" (keydown.enter)="open(row)"></tr>
      </table>
      @if (loaded() && rows().length === 0) {
        <p class="empty">Aucune vente ce jour-là.</p>
      }
    </div>
  `,
  styles: `
    .kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 12px; margin-bottom: 16px; }
    .kpi { display: flex; flex-direction: column; gap: 4px; padding: 16px; border-radius: 12px;
      border: 1px solid var(--mat-sys-outline-variant);
      strong { font: var(--mat-sys-headline-small); font-variant-numeric: tabular-nums; } }
    .clickable { cursor: pointer; &:hover { background: var(--mat-sys-surface-container); } }
    .struck { text-decoration: line-through; color: var(--mat-sys-on-surface-variant); }
  `,
})
export class Sales {
  private readonly api = inject(SalesApi);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  protected readonly viewAll = this.auth.can('SALES_VIEW_ALL');
  protected readonly currency = this.auth.session()?.currency ?? 'DZD';
  protected readonly columns = ['id', 'time'].concat(this.viewAll ? ['cashier'] : [], ['items', 'total', 'status']);
  protected readonly maxDate = today();
  protected readonly date = signal(today());
  protected readonly rows = signal<SaleSummary[]>([]);
  protected readonly loaded = signal(false);

  protected readonly completed = computed(() => this.rows().filter((s) => s.status === 'COMPLETED'));
  protected readonly cashed = computed(() => this.completed().reduce((sum, s) => sum + Math.round(s.total * 100), 0) / 100);
  protected readonly items = computed(() => this.completed().reduce((sum, s) => sum + s.itemCount, 0));

  constructor() {
    this.load();
  }

  load(): void {
    this.api.list(this.date()).subscribe((rows) => {
      this.rows.set(rows);
      this.loaded.set(true);
    });
  }

  open(sale: SaleSummary): void {
    this.router.navigate(['/sales', sale.id]);
  }
}
