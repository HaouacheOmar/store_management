import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { apiMessage } from '../../core/api-error';
import { Supplier, SupplierApi } from '../../core/api/supplier.api';

@Component({
  selector: 'app-supplier-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ supplier ? 'Modifier le fournisseur' : 'Nouveau fournisseur' }}</h2>
    <mat-dialog-content>
      <form id="supplier-form" class="form-grid" [formGroup]="form" (ngSubmit)="save()">
        <mat-form-field appearance="outline" class="full">
          <mat-label>Nom</mat-label>
          <input matInput formControlName="name" cdkFocusInitial />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Téléphone</mat-label>
          <input matInput type="tel" formControlName="phone" autocomplete="tel" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Email</mat-label>
          <input matInput type="email" formControlName="email" autocomplete="email" />
          @if (form.controls.email.hasError('email')) {
            <mat-error>Email invalide</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Adresse</mat-label>
          <input matInput formControlName="address" autocomplete="street-address" />
        </mat-form-field>
      </form>
      @if (error()) {
        <p class="form-error" role="alert">{{ error() }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Annuler</button>
      <button mat-flat-button type="submit" form="supplier-form" [disabled]="saving()">Enregistrer</button>
    </mat-dialog-actions>
  `,
})
export class SupplierDialog {
  protected readonly supplier = inject<Supplier | null>(MAT_DIALOG_DATA);
  private readonly api = inject(SupplierApi);
  private readonly ref = inject(MatDialogRef<SupplierDialog, boolean>);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    name: [this.supplier?.name ?? '', [Validators.required, Validators.maxLength(120)]],
    phone: [this.supplier?.phone ?? '', Validators.maxLength(30)],
    email: [this.supplier?.email ?? '', [Validators.email, Validators.maxLength(180)]],
    address: [this.supplier?.address ?? '', Validators.maxLength(255)],
  });

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    const v = this.form.getRawValue();
    const input = { name: v.name, phone: v.phone || null, email: v.email || null, address: v.address || null };
    const request = this.supplier ? this.api.update(this.supplier.id, input) : this.api.create(input);
    request.subscribe({
      next: () => this.ref.close(true),
      error: (e: HttpErrorResponse) => {
        this.error.set(apiMessage(e));
        this.saving.set(false);
      },
    });
  }
}
