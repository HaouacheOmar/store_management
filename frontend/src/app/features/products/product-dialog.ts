import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { apiMessage } from '../../core/api-error';
import { CatalogApi, Category, Product } from '../../core/api/catalog.api';

export interface ProductDialogData {
  product: Product | null;
  categories: Category[];
  currency: string;
}

/** Création / édition d'un produit. Le stock n'est saisi qu'à la création (ensuite : restockage, ventes). */
@Component({
  selector: 'app-product-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ product ? 'Modifier le produit' : 'Nouveau produit' }}</h2>
    <mat-dialog-content>
      <form id="product-form" class="form-grid" [formGroup]="form" (ngSubmit)="save()">
        <mat-form-field appearance="outline" class="full">
          <mat-label>Nom</mat-label>
          <input matInput formControlName="name" cdkFocusInitial />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Catégorie</mat-label>
          <mat-select formControlName="categoryId">
            @for (c of data.categories; track c.id) {
              <mat-option [value]="c.id">{{ c.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Code (optionnel)</mat-label>
          <input matInput formControlName="code" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Prix de vente</mat-label>
          <input matInput type="number" min="0.01" step="0.01" formControlName="price" />
          <span matTextSuffix>{{ currency }}</span>
          @if (form.controls.price.hasError('min')) {
            <mat-error>Le prix doit être positif</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>{{ product ? 'Stock actuel' : 'Quantité initiale' }}</mat-label>
          <input matInput type="number" min="0" step="1" formControlName="stockQty" />
          @if (product) {
            <mat-hint>Modifiable par restockage</mat-hint>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Seuil d'alerte</mat-label>
          <input matInput type="number" min="0" step="1" formControlName="alertThreshold" />
          <mat-hint>Alerte si stock ≤ seuil</mat-hint>
        </mat-form-field>
      </form>
      @if (error()) {
        <p class="form-error" role="alert">{{ error() }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Annuler</button>
      <button mat-flat-button type="submit" form="product-form" [disabled]="saving()">Enregistrer</button>
    </mat-dialog-actions>
  `,
})
export class ProductDialog {
  protected readonly data = inject<ProductDialogData>(MAT_DIALOG_DATA);
  private readonly api = inject(CatalogApi);
  private readonly ref = inject(MatDialogRef<ProductDialog, boolean>);
  protected readonly product = this.data.product;
  protected readonly currency = this.data.currency;
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    name: [this.product?.name ?? '', [Validators.required, Validators.maxLength(120)]],
    code: [this.product?.code ?? '', Validators.maxLength(50)],
    categoryId: [this.product?.categoryId ?? this.data.categories[0]?.id, Validators.required],
    price: [this.product?.price ?? (null as unknown as number), [Validators.required, Validators.min(0.01)]],
    stockQty: [
      { value: this.product?.stockQty ?? 0, disabled: !!this.product },
      [Validators.required, Validators.min(0)],
    ],
    alertThreshold: [this.product?.alertThreshold ?? 5, [Validators.required, Validators.min(0)]],
  });

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    const value = this.form.getRawValue();
    const input = { ...value, code: value.code.trim() || null, version: this.product?.version };
    const request = this.product
      ? this.api.updateProduct(this.product.id, input)
      : this.api.createProduct(input);
    request.subscribe({
      next: () => this.ref.close(true),
      error: (e: HttpErrorResponse) => {
        this.error.set(apiMessage(e));
        this.saving.set(false);
      },
    });
  }
}
