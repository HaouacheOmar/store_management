import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { apiMessage } from '../../core/api-error';
import { User, UsersApi } from '../../core/api/users.api';
import { PERMISSION_LABELS } from '../../core/auth/labels';

/** Droits d'un responsable : « Encaisser » est toujours inclus (PRD §2.1). */
@Component({
  selector: 'app-permissions-dialog',
  imports: [MatDialogModule, MatCheckboxModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Droits de {{ data.user.name }}</h2>
    <mat-dialog-content>
      <p class="muted">Pris en compte à sa prochaine connexion.</p>
      <div class="perm-list">
        @for (p of data.catalog; track p) {
          <mat-checkbox [checked]="selected().has(p)" [disabled]="p === 'SALE_CREATE'" (change)="toggle(p, $event.checked)">
            {{ labels[p] ?? p }}
          </mat-checkbox>
        }
      </div>
      @if (error()) {
        <p class="form-error" role="alert">{{ error() }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Annuler</button>
      <button mat-flat-button (click)="save()" [disabled]="saving()">Enregistrer</button>
    </mat-dialog-actions>
  `,
  styles: `.perm-list { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 4px 16px; }`,
})
export class PermissionsDialog {
  protected readonly data = inject<{ user: User; catalog: string[] }>(MAT_DIALOG_DATA);
  private readonly api = inject(UsersApi);
  private readonly ref = inject(MatDialogRef<PermissionsDialog, boolean>);
  protected readonly labels = PERMISSION_LABELS;
  protected readonly selected = signal(new Set([...this.data.user.permissions, 'SALE_CREATE']));
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  toggle(permission: string, checked: boolean): void {
    this.selected.update((set) => {
      const next = new Set(set);
      if (checked) next.add(permission);
      else next.delete(permission);
      return next;
    });
  }

  save(): void {
    this.saving.set(true);
    this.api.setPermissions(this.data.user.id, [...this.selected()]).subscribe({
      next: () => this.ref.close(true),
      error: (e: HttpErrorResponse) => {
        this.error.set(apiMessage(e));
        this.saving.set(false);
      },
    });
  }
}
