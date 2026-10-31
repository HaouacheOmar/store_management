import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { apiMessage } from '../../core/api-error';
import { Supplier, SupplierApi } from '../../core/api/supplier.api';
import { AuthService } from '../../core/auth/auth.service';
import { confirmDialog } from '../../shared/confirm-dialog';
import { SupplierDialog } from './supplier-dialog';

@Component({
  selector: 'app-suppliers',
  imports: [RouterLink, MatTableModule, MatButtonModule, MatIconModule],
  template: `
    <header class="page-header">
      <h1>Fournisseurs</h1>
      @if (canWrite) {
        <button mat-flat-button (click)="edit(null)"><mat-icon>add</mat-icon>Nouveau fournisseur</button>
      }
    </header>

    <div class="table-wrap">
      <table mat-table [dataSource]="rows()">
        <ng-container matColumnDef="name">
          <th mat-header-cell *matHeaderCellDef>Nom</th>
          <td mat-cell *matCellDef="let s">
            {{ s.name }}
            <div><a class="muted" routerLink="/restocks" [queryParams]="{ supplierId: s.id }">Historique des restockages</a></div>
          </td>
        </ng-container>
        <ng-container matColumnDef="contact">
          <th mat-header-cell *matHeaderCellDef>Contact</th>
          <td mat-cell *matCellDef="let s">
            @if (s.phone) {
              <a [href]="'tel:' + s.phone">{{ s.phone }}</a>
            }
            @if (s.email) {
              <div><a [href]="'mailto:' + s.email">{{ s.email }}</a></div>
            }
          </td>
        </ng-container>
        <ng-container matColumnDef="address">
          <th mat-header-cell *matHeaderCellDef>Adresse</th>
          <td mat-cell *matCellDef="let s">{{ s.address }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let s" class="actions">
            <button mat-icon-button (click)="edit(s)" [attr.aria-label]="'Modifier ' + s.name"><mat-icon>edit</mat-icon></button>
            <button mat-icon-button (click)="remove(s)" [attr.aria-label]="'Supprimer ' + s.name"><mat-icon>delete</mat-icon></button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
      @if (loaded() && rows().length === 0) {
        <p class="empty">Aucun fournisseur.</p>
      }
    </div>
  `,
})
export class Suppliers {
  private readonly api = inject(SupplierApi);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);

  protected readonly canWrite = inject(AuthService).can('SUPPLIER_WRITE');
  protected readonly columns = ['name', 'contact', 'address'].concat(this.canWrite ? ['actions'] : []);
  protected readonly rows = signal<Supplier[]>([]);
  protected readonly loaded = signal(false);

  constructor() {
    this.load();
  }

  edit(supplier: Supplier | null): void {
    this.dialog
      .open(SupplierDialog, { data: supplier, width: '520px' })
      .afterClosed()
      .subscribe((saved) => {
        if (!saved) return;
        this.snack.open(supplier ? 'Fournisseur modifié' : 'Fournisseur ajouté', undefined, { duration: 2500 });
        this.load();
      });
  }

  remove(supplier: Supplier): void {
    confirmDialog(this.dialog, {
      title: 'Supprimer le fournisseur',
      message: `« ${supplier.name} » ne sera plus proposé au restockage. L'historique est conservé.`,
      action: 'Supprimer',
    }).subscribe((ok) => {
      if (!ok) return;
      this.api.delete(supplier.id).subscribe({
        next: () => {
          this.snack.open('Fournisseur supprimé', undefined, { duration: 2500 });
          this.load();
        },
        error: (e: HttpErrorResponse) => this.snack.open(apiMessage(e), 'OK', { duration: 5000 }),
      });
    });
  }

  private load(): void {
    this.api.list().subscribe((s) => {
      this.rows.set(s);
      this.loaded.set(true);
    });
  }
}
