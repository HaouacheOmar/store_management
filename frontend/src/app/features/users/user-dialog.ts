import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { apiMessage } from '../../core/api-error';
import { UsersApi } from '../../core/api/users.api';

/** Nouveau compte : un caissier (GUEST), qui pourra ensuite être promu responsable. */
@Component({
  selector: 'app-user-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Nouveau caissier</h2>
    <mat-dialog-content>
      <form id="user-form" class="form-grid" [formGroup]="form" (ngSubmit)="save()">
        <mat-form-field appearance="outline" class="full">
          <mat-label>Nom</mat-label>
          <input matInput formControlName="name" autocomplete="off" cdkFocusInitial />
        </mat-form-field>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Email (identifiant de connexion)</mat-label>
          <input matInput type="email" formControlName="email" autocomplete="off" />
          @if (form.controls.email.hasError('email')) {
            <mat-error>Email invalide</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Mot de passe initial</mat-label>
          <input matInput type="password" formControlName="password" autocomplete="new-password" />
          <mat-hint>8 caractères minimum, à transmettre à la personne</mat-hint>
        </mat-form-field>
      </form>
      @if (error()) {
        <p class="form-error" role="alert">{{ error() }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Annuler</button>
      <button mat-flat-button type="submit" form="user-form" [disabled]="saving()">Créer le compte</button>
    </mat-dialog-actions>
  `,
})
export class UserDialog {
  private readonly api = inject(UsersApi);
  private readonly ref = inject(MatDialogRef<UserDialog, boolean>);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(NonNullableFormBuilder).group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
  });

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.api.create(this.form.getRawValue()).subscribe({
      next: () => this.ref.close(true),
      error: (e: HttpErrorResponse) => {
        this.error.set(apiMessage(e));
        this.saving.set(false);
      },
    });
  }
}
