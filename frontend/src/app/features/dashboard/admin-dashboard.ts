import { CurrencyPipe, DatePipe, formatCurrency, NgTemplateOutlet } from '@angular/common';
import { Component, computed, effect, inject, LOCALE_ID, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ChartConfiguration } from 'chart.js';
import { CatalogApi, Category } from '../../core/api/catalog.api';
import { Period, RankBy, Ranked, ReportingApi, Revenue } from '../../core/api/reporting.api';
import { AuthService } from '../../core/auth/auth.service';
import { ChartCanvas, VIZ } from '../../shared/chart';
import { localDay } from '../../shared/dates';
import { StoreToday } from './store-today';

type Preset = 'today' | '7d' | '30d' | 'custom';

/** PRD M8 : chiffre d'affaires, KPIs, courbe journalière, produits et catégories les plus vendus. */
@Component({
  selector: 'app-admin-dashboard',
  imports: [
    FormsModule,
    NgTemplateOutlet,
    CurrencyPipe,
    DatePipe,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    ChartCanvas,
    StoreToday,
  ],
  template: `
    <!-- filtres de période : une seule ligne, au-dessus de tous les graphes -->
    <div class="filters">
      <mat-button-toggle-group [value]="preset()" (change)="setPreset($event.value)" aria-label="Période">
        <mat-button-toggle value="today">Aujourd'hui</mat-button-toggle>
        <mat-button-toggle value="7d">7 jours</mat-button-toggle>
        <mat-button-toggle value="30d">30 jours</mat-button-toggle>
        <mat-button-toggle value="custom">Période…</mat-button-toggle>
      </mat-button-toggle-group>
      @if (preset() === 'custom') {
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>Du</mat-label>
          <input matInput type="date" [ngModel]="from()" (ngModelChange)="from.set($event)" [max]="to()" />
        </mat-form-field>
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>Au</mat-label>
          <input matInput type="date" [ngModel]="to()" (ngModelChange)="to.set($event)" [min]="from()" [max]="maxDay" />
        </mat-form-field>
      }
    </div>

    @if (revenue(); as r) {
      <section class="card kpi-row">
        <div class="kpi hero-kpi">
          <span class="muted">Chiffre d'affaires · {{ r.from | date: 'dd/MM' }} → {{ r.to | date: 'dd/MM' }}</span>
          <strong class="hero">{{ r.revenue | currency: currency : 'symbol' : '1.2-2' }}</strong>
        </div>
        <div class="kpi"><span class="muted">Tickets</span><strong>{{ r.tickets }}</strong></div>
        <div class="kpi"><span class="muted">Panier moyen</span><strong>{{ r.averageBasket | currency: currency : 'symbol' : '1.2-2' }}</strong></div>
        <div class="kpi"><span class="muted">Articles vendus</span><strong>{{ r.itemsSold }}</strong></div>
        <div class="kpi"><span class="muted">Produits en rupture</span><strong [class.alert]="outOfStock() > 0">{{ outOfStock() }}</strong></div>
      </section>

      <section class="card">
        <h2>Chiffre d'affaires par jour</h2>
        <app-chart [config]="revenueChart()" label="Courbe du chiffre d'affaires par jour" [height]="240" />
        <details>
          <summary>Voir les données</summary>
          <table class="data-table">
            <tr><th>Jour</th><th class="num">CA</th><th class="num">Tickets</th></tr>
            @for (d of r.series; track d.date) {
              <tr><td>{{ d.date | date: 'dd/MM/yyyy' }}</td><td class="num">{{ d.revenue | currency: currency : 'symbol' : '1.2-2' }}</td><td class="num">{{ d.tickets }}</td></tr>
            }
          </table>
        </details>
      </section>
    }

    <div class="charts-grid">
      <section class="card">
        <h2>Produits les plus vendus</h2>
        <div class="filters compact">
          <mat-form-field appearance="outline" subscriptSizing="dynamic">
            <mat-label>Catégorie</mat-label>
            <mat-select [value]="productCategory()" (selectionChange)="productCategory.set($event.value)">
              <mat-option [value]="null">Toutes</mat-option>
              @for (c of categories(); track c.id) {
                <mat-option [value]="c.id">{{ c.name }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <ng-container *ngTemplateOutlet="rankControls; context: { limit: productLimit, by: productBy }" />
        </div>
        <ng-container *ngTemplateOutlet="ranking; context: { rows: topProducts(), config: productChart(), label: 'Produits les plus vendus' }" />
      </section>

      <section class="card">
        <h2>Catégories les plus vendues</h2>
        <div class="filters compact">
          <ng-container *ngTemplateOutlet="rankControls; context: { limit: categoryLimit, by: categoryBy }" />
        </div>
        <ng-container *ngTemplateOutlet="ranking; context: { rows: topCategories(), config: categoryChart(), label: 'Catégories les plus vendues' }" />
      </section>
    </div>

    <h2 class="section-title">Aujourd'hui</h2>
    <app-store-today />

    <ng-template #rankControls let-limit="limit" let-by="by">
      <mat-form-field appearance="outline" subscriptSizing="dynamic" class="narrow">
        <mat-label>Top</mat-label>
        <mat-select [value]="limit()" (selectionChange)="limit.set($event.value)">
          @for (n of [5, 10, 20]; track n) {
            <mat-option [value]="n">{{ n }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-button-toggle-group [value]="by()" (change)="by.set($event.value)" aria-label="Classer par">
        <mat-button-toggle value="QTY">Quantité</mat-button-toggle>
        <mat-button-toggle value="REVENUE">Montant</mat-button-toggle>
      </mat-button-toggle-group>
    </ng-template>

    <ng-template #ranking let-rows="rows" let-config="config" let-label="label">
      @if (rows.length === 0) {
        <p class="empty">Aucune vente sur la période.</p>
      } @else {
        <app-chart [config]="config" [label]="label" [height]="40 + rows.length * 32" />
        <details>
          <summary>Voir les données</summary>
          <table class="data-table">
            <tr><th>Nom</th><th class="num">Quantité</th><th class="num">Montant</th></tr>
            @for (row of rows; track row.id) {
              <tr><td>{{ row.name }}</td><td class="num">{{ row.qty }}</td><td class="num">{{ row.revenue | currency: currency : 'symbol' : '1.2-2' }}</td></tr>
            }
          </table>
        </details>
      }
    </ng-template>
  `,
  styles: `
    .kpi-row { display: grid; grid-template-columns: 2fr repeat(4, 1fr); gap: 16px; margin-bottom: 16px; }
    @media (max-width: 900px) { .kpi-row { grid-template-columns: 1fr 1fr; } .hero-kpi { grid-column: 1 / -1; } }
    .kpi { display: flex; flex-direction: column; gap: 4px; justify-content: flex-end;
      strong:not(.hero) { font: var(--mat-sys-headline-small); } .alert { color: var(--mat-sys-error); } }
    .charts-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(360px, 1fr)); gap: 16px; margin: 16px 0; }
    .compact { margin-bottom: 8px; }
    .narrow { width: 90px; }
    .section-title { font: var(--mat-sys-title-large); margin: 24px 0 12px; }
    details { margin-top: 12px; summary { cursor: pointer; color: var(--mat-sys-primary); } }
    .data-table { width: 100%; border-collapse: collapse; margin-top: 8px; font: var(--mat-sys-body-small);
      th, td { padding: 4px 8px; border-bottom: 1px solid var(--mat-sys-outline-variant); text-align: left; }
      .num { text-align: right; font-variant-numeric: tabular-nums; } }
  `,
})
export class AdminDashboard {
  private readonly reporting = inject(ReportingApi);
  private readonly catalog = inject(CatalogApi);
  private readonly locale = inject(LOCALE_ID);
  protected readonly currency = inject(AuthService).session()?.currency ?? 'DZD';
  protected readonly maxDay = localDay();

  protected readonly preset = signal<Preset>('7d');
  protected readonly from = signal(localDay(-6));
  protected readonly to = signal(localDay());
  private readonly period = computed<Period>(() => ({ from: this.from(), to: this.to() }));

  protected readonly productCategory = signal<number | null>(null);
  protected readonly productLimit = signal(10);
  protected readonly productBy = signal<RankBy>('QTY');
  protected readonly categoryLimit = signal(10);
  protected readonly categoryBy = signal<RankBy>('QTY');

  protected readonly categories = signal<Category[]>([]);
  protected readonly revenue = signal<Revenue | null>(null);
  protected readonly topProducts = signal<Ranked[]>([]);
  protected readonly topCategories = signal<Ranked[]>([]);
  protected readonly outOfStock = signal(0);

  constructor() {
    this.catalog.categories().subscribe((c) => this.categories.set(c));
    this.catalog
      .products({ q: '', categoryId: null, stock: 'OUT', page: 0, size: 1 })
      .subscribe((p) => this.outOfStock.set(p.page.totalElements));

    // chaque requête est relancée quand ses filtres changent ; la précédente est annulée
    effect((onCleanup) => {
      const sub = this.reporting.revenue(this.period()).subscribe((r) => this.revenue.set(r));
      onCleanup(() => sub.unsubscribe());
    });
    effect((onCleanup) => {
      const sub = this.reporting
        .topProducts(this.period(), this.productLimit(), this.productBy(), this.productCategory())
        .subscribe((rows) => this.topProducts.set(rows));
      onCleanup(() => sub.unsubscribe());
    });
    effect((onCleanup) => {
      const sub = this.reporting
        .topCategories(this.period(), this.categoryLimit(), this.categoryBy())
        .subscribe((rows) => this.topCategories.set(rows));
      onCleanup(() => sub.unsubscribe());
    });
  }

  setPreset(preset: Preset): void {
    this.preset.set(preset);
    const days = { today: 0, '7d': 6, '30d': 29, custom: null }[preset];
    if (days === null) return;
    this.from.set(localDay(-days));
    this.to.set(localDay());
  }

  private money(value: number): string {
    return formatCurrency(value, this.locale, this.currency, this.currency, '1.2-2');
  }

  /** Une série, un axe : ligne 2px + voile 10 %, réticule et infobulle au survol. */
  protected readonly revenueChart = computed<ChartConfiguration>(() => {
    const series = this.revenue()?.series ?? [];
    return {
      type: 'line',
      data: {
        labels: series.map((d) => d.date.slice(8, 10) + '/' + d.date.slice(5, 7)),
        datasets: [{
          label: "Chiffre d'affaires",
          data: series.map((d) => d.revenue),
          borderColor: VIZ.series,
          backgroundColor: VIZ.seriesWash,
          fill: 'origin',
          borderWidth: 2,
          tension: 0, // pas de lissage : une courbe ne doit pas inventer de valeurs entre deux jours
          pointRadius: series.length === 1 ? 4 : 0,
          pointHoverRadius: 5,
          pointBackgroundColor: VIZ.series,
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: 'index', intersect: false },
        plugins: {
          legend: { display: false },
          tooltip: { callbacks: { label: (ctx) => ' ' + this.money(ctx.parsed.y ?? 0) } },
        },
        scales: {
          x: { grid: { display: false }, ticks: { maxTicksLimit: 10 } },
          y: { beginAtZero: true, grid: { color: VIZ.grid }, border: { display: false },
            ticks: { maxTicksLimit: 5, callback: (v) => this.money(Number(v)) } },
        },
      },
    };
  });

  // computed : le graphe n'est recréé que si ses données ou son critère changent
  protected readonly productChart = computed(() => this.barChart(this.topProducts(), this.productBy()));
  protected readonly categoryChart = computed(() => this.barChart(this.topCategories(), this.categoryBy()));

  /** Barres horizontales ≤ 24 px, bout arrondi 4 px, base carrée ; infobulle avec quantité et montant. */
  private barChart(rows: Ranked[], by: RankBy): ChartConfiguration {
    return {
      type: 'bar',
      data: {
        labels: rows.map((r) => r.name),
        datasets: [{
          label: by === 'QTY' ? 'Quantité' : 'Montant',
          data: rows.map((r) => (by === 'QTY' ? r.qty : r.revenue)),
          backgroundColor: VIZ.series,
          hoverBackgroundColor: '#1f63b5',
          borderRadius: 4,
          borderSkipped: 'start',
          maxBarThickness: 24,
        }],
      },
      options: {
        indexAxis: 'y',
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              label: (ctx) => {
                const row = rows[ctx.dataIndex];
                return ` ${row.qty} vendus · ${this.money(row.revenue)}`;
              },
            },
          },
        },
        scales: {
          x: { beginAtZero: true, grid: { color: VIZ.grid }, border: { display: false },
            ticks: { maxTicksLimit: 5, callback: (v) => (by === 'QTY' ? v : this.money(Number(v))) } },
          y: { grid: { display: false }, ticks: { color: VIZ.textPrimary } },
        },
      },
    };
  }
}
