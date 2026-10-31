import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { apiMessage } from '../../core/api-error';
import { Sale, SalesApi } from '../../core/api/sales.api';
import { AuthService } from '../../core/auth/auth.service';
import { confirmDialog } from '../../shared/confirm-dialog';

/** Ticket de caisse 80 mm : seul le bloc .receipt est imprimé (styles.scss, @media print). */
@Component({
  selector: 'app-receipt',
  imports: [CurrencyPipe, DatePipe, RouterLink, MatButtonModule, MatIconModule],
  template: `
    <div class="actions-bar no-print">
      <a mat-flat-button routerLink="/pos"><mat-icon>point_of_sale</mat-icon>Nouvelle vente</a>
      <button mat-stroked-button (click)="print()" [disabled]="!sale()"><mat-icon>print</mat-icon>Imprimer</button>
      <a mat-button routerLink="/sales">Ventes du jour</a>
      @if (canCancel && sale()?.status === 'COMPLETED') {
        <button mat-button class="cancel" (click)="cancel()"><mat-icon>undo</mat-icon>Annuler la vente</button>
      }
    </div>

    @if (sale(); as s) {
      <article class="receipt">
        <header>
          <strong class="store">{{ storeName }}</strong>
          <div>Ticket n° {{ s.id }}</div>
          <div>{{ s.createdAt | date: 'dd/MM/yyyy HH:mm' }}</div>
          <div>Caissier : {{ s.cashierName }}</div>
          @if (s.status === 'CANCELLED') {
            <div class="cancelled">*** VENTE ANNULÉE ***</div>
          }
        </header>
        <hr />
        @for (l of s.lines; track l.productId) {
          <div class="line">
            <div>{{ l.productName }}</div>
            <div class="row">
              <span>{{ l.qty }} × {{ l.unitPrice | currency: currency : 'symbol' : '1.2-2' }}
                @if (l.discountPercent > 0) { (-{{ l.discountPercent }} %) }</span>
              <span>{{ l.lineTotal | currency: currency : 'symbol' : '1.2-2' }}</span>
            </div>
          </div>
        }
        <hr />
        <div class="row total"><span>TOTAL</span><span>{{ s.total | currency: currency : 'symbol' : '1.2-2' }}</span></div>
        <div class="row"><span>Espèces</span><span>{{ s.cashReceived | currency: currency : 'symbol' : '1.2-2' }}</span></div>
        <div class="row"><span>Rendu</span><span>{{ s.changeGiven | currency: currency : 'symbol' : '1.2-2' }}</span></div>
        <hr />
        <footer>Merci de votre visite</footer>
      </article>
    }
  `,
  styles: `
    .actions-bar { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 24px; }
    .cancel { color: var(--mat-sys-error); }
    .receipt {
      width: 80mm; max-width: 100%; box-sizing: border-box; padding: 16px; margin: 0 auto;
      font: 13px/1.4 'Courier New', monospace; background: #fff; color: #000;
      border: 1px dashed #999; border-radius: 4px;
    }
    header, footer { text-align: center; }
    .store { font-size: 16px; }
    .cancelled { margin-top: 6px; font-weight: bold; }
    hr { border: none; border-top: 1px dashed #000; margin: 8px 0; }
    .line { margin-bottom: 4px; }
    .row { display: flex; justify-content: space-between; gap: 8px; }
    .total { font-weight: bold; font-size: 15px; }
  `,
})
export class Receipt {
  private readonly api = inject(SalesApi);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);

  protected readonly storeName = this.auth.session()?.storeName ?? '';
  protected readonly currency = this.auth.session()?.currency ?? 'DZD';
  protected readonly canCancel = this.auth.can('SALE_CANCEL');
  protected readonly sale = signal<Sale | null>(null);

  constructor() {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    const autoPrint = this.route.snapshot.queryParamMap.has('print');
    this.api.get(id).subscribe({
      next: (sale) => {
        this.sale.set(sale);
        // juste après l'encaissement : on laisse le ticket se rendre, puis impression
        if (autoPrint) setTimeout(() => this.print(), 300);
      },
      error: (e: HttpErrorResponse) => this.snack.open(apiMessage(e), 'OK', { duration: 5000 }),
    });
  }

  print(): void {
    window.print();
  }

  cancel(): void {
    const s = this.sale()!;
    confirmDialog(this.dialog, {
      title: `Annuler la vente n° ${s.id}`,
      message: 'Les produits seront remis en stock et la vente sortira des totaux.',
      action: 'Annuler la vente',
    }).subscribe((ok) => {
      if (!ok) return;
      this.api.cancel(s.id).subscribe({
        next: (cancelled) => {
          this.sale.set(cancelled);
          this.snack.open('Vente annulée', undefined, { duration: 2500 });
        },
        error: (e: HttpErrorResponse) => this.snack.open(apiMessage(e), 'OK', { duration: 5000 }),
      });
    });
  }
}
