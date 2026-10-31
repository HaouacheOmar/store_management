import { Component, inject } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { AuthService } from '../../core/auth/auth.service';
import { PERMISSION_LABELS, ROLE_LABELS } from '../../core/auth/labels';

@Component({
  selector: 'app-dashboard',
  imports: [MatCardModule, MatChipsModule],
  template: `
    @if (session(); as s) {
      <h1>Bonjour {{ s.name }}</h1>
      <mat-card appearance="outlined">
        <mat-card-header>
          <mat-card-title>{{ s.storeName }}</mat-card-title>
          <mat-card-subtitle>{{ roles[s.role] }}</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <p>Vos droits :</p>
          <mat-chip-set aria-label="Vos droits">
            @for (p of s.permissions; track p) {
              <mat-chip>{{ permissions[p] ?? p }}</mat-chip>
            }
          </mat-chip-set>
        </mat-card-content>
      </mat-card>
    }
  `,
  styles: `h1 { font: var(--mat-sys-headline-small); margin: 0 0 16px; }`,
})
export class Dashboard {
  protected readonly session = inject(AuthService).session;
  protected readonly roles = ROLE_LABELS;
  protected readonly permissions = PERMISSION_LABELS;
}
