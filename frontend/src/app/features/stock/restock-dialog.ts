import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { apiMessage } from '../../core/api-error';
import { CatalogApi, Product } from '../../core/api/catalog.api';
import { Supplier, SupplierApi } from '../../core/api/supplier.api';

/** Restockage d'un produit : le fournisseur est obligatoire (PRD US-3.3). */
@Component({
  selector: 'app-restock-dialog',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
  ],
  template: `
    <h2 mat-dialog-title>Restocker « {{ product.name }} »</h2>
    <mat-dialog-content>
      <p class="muted">Stock actuel : {{ product.stockQty }} (seuil d'alerte {{ product.alertThreshold }})</p>
      @if (loaded() && suppliers().length === 0) {
        <p class="form-error">
          Aucun fournisseur. <a routerLink="/suppliers" mat-dialog-close>Ajoutez-en un</a> avant de restocker.
        </p>
      }
      <form id="restock-form" class="form-grid" [formGroup]="form" (ngSubmit)="save()">
        <mat-form-field appearance="outline" class="full">
          <mat-label>Fournisseur</mat-label>
          <mat-select formControlName="supplierId">
            @for (s of suppliers(); track s.id) {
              <mat-option [value]="s.id">{{ s.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Quantité reçue</mat-label>
          <input matInput type="number" min="1" step="1" formControlName="qty" cdkFocusInitial />
          @if (form.controls.qty.hasError('min')) {
            <mat-error>Au moins 1</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Prix d'achat unitaire</mat-label>
          <input matInput type="number" min="0" step="0.01" formControlName="unitCost" />
          <mat-hint>Optionnel</mat-hint>
        </mat-form-field>
      </form>
      @if (error()) {
        <p class="form-error" role="alert">{{ error() }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Annuler</button>
      <button mat-flat-button type="submit" form="restock-form" [disabled]="saving()">Restocker</button>
    </mat-dialog-actions>
  `,
})
export class RestockDialog {
  protected readonly product = inject<Product>(MAT_DIALOG_DATA);
  private readonly api = inject(CatalogApi);
  private readonly ref = inject(MatDialogRef<RestockDialog, boolean>);
  protected readonly suppliers = signal<Supplier[]>([]);
  protected readonly loaded = signal(false);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    supplierId: [null as number | null, Validators.required],
    qty: [null as number | null, [Validators.required, Validators.min(1)]],
    unitCost: [null as number | null, Validators.min(0)],
  });

  constructor() {
    inject(SupplierApi)
      .list()
      .subscribe((s) => {
        this.suppliers.set(s);
        this.loaded.set(true);
        if (s.length === 1) this.form.controls.supplierId.setValue(s[0].id);
      });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    const v = this.form.getRawValue();
    this.api
      .restock({ productId: this.product.id, supplierId: v.supplierId!, qty: v.qty!, unitCost: v.unitCost })
      .subscribe({
        next: () => this.ref.close(true),
        error: (e: HttpErrorResponse) => {
          this.error.set(apiMessage(e));
          this.saving.set(false);
        },
      });
  }
}
