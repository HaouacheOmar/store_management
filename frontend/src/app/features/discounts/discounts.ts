import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { Observable } from 'rxjs';
import { apiMessage } from '../../core/api-error';
import { CatalogApi, Discount, DiscountStatus } from '../../core/api/catalog.api';
import { AuthService } from '../../core/auth/auth.service';
import { confirmDialog } from '../../shared/confirm-dialog';
import { DiscountDialog } from './discount-dialog';

/** Remises ponctuelles (PRD M5) : à venir = modifiable, en cours = à terminer, passées = lecture. */
@Component({
  selector: 'app-discounts',
  imports: [DatePipe, MatTableModule, MatButtonModule, MatButtonToggleModule, MatIconModule],
  template: `
    <header class="page-header">
      <h1>Remises</h1>
      @if (canWrite) {
        <button mat-flat-button (click)="edit(null)"><mat-icon>add</mat-icon>Nouvelle remise</button>
      }
    </header>

    <div class="filters">
      <mat-button-toggle-group [value]="status()" (change)="status.set($event.value); load()" aria-label="Statut">
        <mat-button-toggle value="ACTIVE">En cours</mat-button-toggle>
        <mat-button-toggle value="UPCOMING">À venir</mat-button-toggle>
        <mat-button-toggle value="PAST">Passées</mat-button-toggle>
      </mat-button-toggle-group>
    </div>

    <div class="table-wrap">
      <table mat-table [dataSource]="rows()">
        <ng-container matColumnDef="label">
          <th mat-header-cell *matHeaderCellDef>Remise</th>
          <td mat-cell *matCellDef="let d"><span class="discount-badge">-{{ d.percent }} %</span> {{ d.label }}</td>
        </ng-container>
        <ng-container matColumnDef="target">
          <th mat-header-cell *matHeaderCellDef>Sur</th>
          <td mat-cell *matCellDef="let d">
            {{ d.targetName ?? '(supprimé)' }}
            <div class="muted">{{ d.targetType === 'PRODUCT' ? 'Produit' : 'Catégorie' }}</div>
          </td>
        </ng-container>
        <ng-container matColumnDef="period">
          <th mat-header-cell *matHeaderCellDef>Période</th>
          <td mat-cell *matCellDef="let d">
            {{ d.startsAt | date: 'dd/MM/yyyy HH:mm' }} → {{ d.endsAt | date: 'dd/MM/yyyy HH:mm' }}
          </td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let d" class="actions">
            @if (d.status === 'UPCOMING') {
              <button mat-icon-button (click)="edit(d)" [attr.aria-label]="'Modifier ' + d.label"><mat-icon>edit</mat-icon></button>
              <button mat-icon-button (click)="remove(d)" [attr.aria-label]="'Supprimer ' + d.label"><mat-icon>delete</mat-icon></button>
            }
            @if (d.status === 'ACTIVE') {
              <button mat-stroked-button (click)="end(d)">Terminer</button>
            }
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
      @if (loaded() && rows().length === 0) {
        <p class="empty">Aucune remise {{ emptyLabel[status()] }}.</p>
      }
    </div>
  `,
})
export class Discounts {
  private readonly api = inject(CatalogApi);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);

  protected readonly canWrite = inject(AuthService).can('DISCOUNT_WRITE');
  protected readonly columns = ['label', 'target', 'period'].concat(this.canWrite ? ['actions'] : []);
  protected readonly emptyLabel: Record<DiscountStatus, string> = {
    ACTIVE: 'en cours',
    UPCOMING: 'à venir',
    PAST: 'passée',
  };
  protected readonly status = signal<DiscountStatus>('ACTIVE');
  protected readonly rows = signal<Discount[]>([]);
  protected readonly loaded = signal(false);

  constructor() {
    this.load();
  }

  load(): void {
    this.api.discounts(this.status()).subscribe((d) => {
      this.rows.set(d);
      this.loaded.set(true);
    });
  }

  edit(discount: Discount | null): void {
    this.dialog
      .open(DiscountDialog, { data: discount, width: '600px' })
      .afterClosed()
      .subscribe((saved) => saved && this.done(discount ? 'Remise modifiée' : 'Remise créée'));
  }

  remove(d: Discount): void {
    confirmDialog(this.dialog, {
      title: 'Supprimer la remise',
      message: `Supprimer « ${d.label} » ? Elle n'a pas encore commencé.`,
      action: 'Supprimer',
    }).subscribe((ok) => ok && this.run(this.api.deleteDiscount(d.id), 'Remise supprimée'));
  }

  end(d: Discount): void {
    confirmDialog(this.dialog, {
      title: 'Terminer la remise',
      message: `« ${d.label} » cessera de s'appliquer immédiatement.`,
      action: 'Terminer',
    }).subscribe((ok) => ok && this.run(this.api.endDiscount(d.id), 'Remise terminée'));
  }

  private run(request: Observable<unknown>, success: string): void {
    request.subscribe({
      next: () => this.done(success),
      error: (e: HttpErrorResponse) => this.snack.open(apiMessage(e), 'OK', { duration: 5000 }),
    });
  }

  private done(message: string): void {
    this.snack.open(message, undefined, { duration: 2500 });
    this.load();
  }
}
