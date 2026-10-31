import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router, RouterLink } from '@angular/router';
import { apiMessage } from '../../core/api-error';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <main class="auth-page">
      <mat-card class="auth-card" appearance="outlined">
        <h1>Créer mon magasin</h1>
        <p class="subtitle">Vous serez l'administrateur du magasin.</p>
        <form [formGroup]="form" (ngSubmit)="submit()">
          <mat-form-field appearance="outline">
            <mat-label>Nom du magasin</mat-label>
            <input matInput formControlName="storeName" autocomplete="organization" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Votre nom</mat-label>
            <input matInput formControlName="name" autocomplete="name" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Email</mat-label>
            <input matInput type="email" formControlName="email" autocomplete="email" />
            @if (form.controls.email.hasError('email')) {
              <mat-error>Email invalide</mat-error>
            }
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Mot de passe</mat-label>
            <input matInput type="password" formControlName="password" autocomplete="new-password" />
            <mat-hint>8 caractères minimum</mat-hint>
            @if (form.controls.password.hasError('minlength')) {
              <mat-error>8 caractères minimum</mat-error>
            }
          </mat-form-field>
          @if (error()) {
            <p class="form-error" role="alert">{{ error() }}</p>
          }
          <button mat-flat-button type="submit" [disabled]="loading()">Créer mon magasin</button>
        </form>
        <p class="switch">Déjà inscrit ? <a routerLink="/login">Se connecter</a></p>
      </mat-card>
    </main>
  `,
})
export class Register {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(NonNullableFormBuilder).group({
    storeName: ['', [Validators.required, Validators.maxLength(120)]],
    name: ['', [Validators.required, Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.auth.register(this.form.getRawValue()).subscribe({
      next: () => this.router.navigate(['/']),
      error: (e: HttpErrorResponse) => {
        this.error.set(apiMessage(e));
        this.loading.set(false);
      },
    });
  }
}
