import { CurrencyPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { ReportingApi, Today } from '../../core/api/reporting.api';
import { AuthService } from '../../core/auth/auth.service';

/** PRD M7 : encaissé du jour par caissier et produits vendus aujourd'hui (SALES_VIEW_ALL). */
@Component({
  selector: 'app-store-today',
  imports: [CurrencyPipe, MatTableModule],
  template: `
    @if (today(); as t) {
      <div class="today-grid">
        <section class="card">
          <h2>Encaissé aujourd'hui par caissier</h2>
          <table mat-table [dataSource]="t.cashiers">
            <ng-container matColumnDef="name">
              <th mat-header-cell *matHeaderCellDef>Caissier</th>
              <td mat-cell *matCellDef="let c">{{ c.name }}</td>
            </ng-container>
            <ng-container matColumnDef="tickets">
              <th mat-header-cell *matHeaderCellDef class="num">Tickets</th>
              <td mat-cell *matCellDef="let c" class="num">{{ c.tickets }}</td>
            </ng-container>
            <ng-container matColumnDef="total">
              <th mat-header-cell *matHeaderCellDef class="num">Encaissé</th>
              <td mat-cell *matCellDef="let c" class="num">{{ c.total | currency: currency : 'symbol' : '1.2-2' }}</td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="['name', 'tickets', 'total']"></tr>
            <tr mat-row *matRowDef="let row; columns: ['name', 'tickets', 'total']"></tr>
          </table>
          @if (t.cashiers.length === 0) {
            <p class="empty">Aucune vente aujourd'hui.</p>
          } @else {
            <p class="sum">Total : <strong>{{ t.cashed | currency: currency : 'symbol' : '1.2-2' }}</strong> · {{ t.tickets }} tickets</p>
          }
        </section>

        <section class="card">
          <h2>Produits vendus aujourd'hui</h2>
          <div class="scroll">
            <table mat-table [dataSource]="t.products">
              <ng-container matColumnDef="name">
                <th mat-header-cell *matHeaderCellDef>Produit</th>
                <td mat-cell *matCellDef="let p">{{ p.name }}</td>
              </ng-container>
              <ng-container matColumnDef="qty">
                <th mat-header-cell *matHeaderCellDef class="num">Qté</th>
                <td mat-cell *matCellDef="let p" class="num">{{ p.qty }}</td>
              </ng-container>
              <ng-container matColumnDef="revenue">
                <th mat-header-cell *matHeaderCellDef class="num">Montant</th>
                <td mat-cell *matCellDef="let p" class="num">{{ p.revenue | currency: currency : 'symbol' : '1.2-2' }}</td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="['name', 'qty', 'revenue']"></tr>
              <tr mat-row *matRowDef="let row; columns: ['name', 'qty', 'revenue']"></tr>
            </table>
          </div>
          @if (t.products.length === 0) {
            <p class="empty">Aucun produit vendu aujourd'hui.</p>
          } @else {
            <p class="sum">{{ t.items }} articles vendus</p>
          }
        </section>
      </div>
    }
  `,
  styles: `
    .today-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 16px; }
    .scroll { max-height: 320px; overflow-y: auto; }
    .sum { margin: 12px 0 0; color: var(--mat-sys-on-surface-variant); }
    td.num, th.num { text-align: right; }
  `,
})
export class StoreToday {
  protected readonly currency = inject(AuthService).session()?.currency ?? 'DZD';
  protected readonly today = signal<Today | null>(null);

  constructor() {
    inject(ReportingApi)
      .today()
      .subscribe((t) => this.today.set(t));
  }
}
