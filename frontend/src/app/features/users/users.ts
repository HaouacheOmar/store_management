import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { Observable } from 'rxjs';
import { apiMessage } from '../../core/api-error';
import { User, UsersApi } from '../../core/api/users.api';
import { PERMISSION_LABELS, ROLE_LABELS } from '../../core/auth/labels';
import { confirmDialog } from '../../shared/confirm-dialog';
import { PermissionsDialog } from './permissions-dialog';
import { UserDialog } from './user-dialog';

/** PRD M1 : comptes du store, promotion caissier ↔ responsable, droits du responsable. */
@Component({
  selector: 'app-users',
  imports: [MatTableModule, MatButtonModule, MatIconModule, MatMenuModule],
  template: `
    <header class="page-header">
      <h1>Utilisateurs</h1>
      <button mat-flat-button (click)="create()"><mat-icon>person_add</mat-icon>Nouveau caissier</button>
    </header>

    <div class="table-wrap">
      <table mat-table [dataSource]="rows()">
        <ng-container matColumnDef="name">
          <th mat-header-cell *matHeaderCellDef>Utilisateur</th>
          <td mat-cell *matCellDef="let u" [class.inactive]="!u.active">
            {{ u.name }}
            <div class="muted">{{ u.email }}</div>
          </td>
        </ng-container>
        <ng-container matColumnDef="role">
          <th mat-header-cell *matHeaderCellDef>Rôle</th>
          <td mat-cell *matCellDef="let u">
            {{ roleLabel(u) }}
            @if (!u.active) {
              <div><span class="stock out">Désactivé</span></div>
            }
          </td>
        </ng-container>
        <ng-container matColumnDef="rights">
          <th mat-header-cell *matHeaderCellDef>Droits</th>
          <td mat-cell *matCellDef="let u" class="rights">
            {{ u.role === 'ADMIN' ? 'Tous' : rightsLabel(u) }}
          </td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let u" class="actions">
            @if (u.role !== 'ADMIN') {
              @if (u.role === 'MANAGER') {
                <button mat-stroked-button (click)="editRights(u)">Droits</button>
              }
              <button mat-icon-button [matMenuTriggerFor]="menu" [attr.aria-label]="'Actions pour ' + u.name">
                <mat-icon>more_vert</mat-icon>
              </button>
              <mat-menu #menu="matMenu">
                @if (u.role === 'GUEST') {
                  <button mat-menu-item (click)="promote(u)"><mat-icon>upgrade</mat-icon>Promouvoir responsable</button>
                } @else {
                  <button mat-menu-item (click)="demote(u)"><mat-icon>south</mat-icon>Repasser caissier</button>
                }
                <button mat-menu-item (click)="toggleActive(u)">
                  <mat-icon>{{ u.active ? 'block' : 'check_circle' }}</mat-icon>{{ u.active ? 'Désactiver' : 'Réactiver' }}
                </button>
              </mat-menu>
            }
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    </div>
    <p class="muted hint">Un changement de rôle ou de droits s'applique à la prochaine connexion de la personne.</p>
  `,
  styles: `
    .inactive { opacity: .6; }
    .rights { max-width: 380px; }
    .hint { margin-top: 12px; }
  `,
})
export class Users {
  private readonly api = inject(UsersApi);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);

  protected readonly columns = ['name', 'role', 'rights', 'actions'];
  protected readonly rows = signal<User[]>([]);
  private catalog: string[] = [];

  constructor() {
    this.api.permissions().subscribe((p) => (this.catalog = p));
    this.load();
  }

  protected roleLabel(user: User): string {
    return ROLE_LABELS[user.role];
  }

  protected rightsLabel(user: User): string {
    return user.permissions.map((p) => PERMISSION_LABELS[p] ?? p).join(', ');
  }

  create(): void {
    this.dialog
      .open(UserDialog, { width: '440px' })
      .afterClosed()
      .subscribe((created) => created && this.done('Compte créé'));
  }

  editRights(user: User): void {
    this.dialog
      .open(PermissionsDialog, { data: { user, catalog: this.catalog }, width: '560px' })
      .afterClosed()
      .subscribe((saved) => saved && this.done('Droits mis à jour'));
  }

  promote(user: User): void {
    this.run(this.api.changeRole(user.id, 'MANAGER'), `${user.name} est responsable`, () => {
      // on enchaîne sur le choix de ses droits : c'est la raison de la promotion
      const promoted = { ...user, role: 'MANAGER' as const };
      this.editRights(promoted);
    });
  }

  demote(user: User): void {
    confirmDialog(this.dialog, {
      title: 'Repasser caissier',
      message: `${user.name} perdra ses droits supplémentaires et ne pourra plus qu'encaisser.`,
      action: 'Confirmer',
    }).subscribe((ok) => ok && this.run(this.api.changeRole(user.id, 'GUEST'), `${user.name} est caissier`));
  }

  toggleActive(user: User): void {
    const action = user.active ? 'Désactiver' : 'Réactiver';
    confirmDialog(this.dialog, {
      title: `${action} le compte`,
      message: user.active
        ? `${user.name} ne pourra plus se connecter. Ses ventes passées sont conservées.`
        : `${user.name} pourra de nouveau se connecter.`,
      action,
    }).subscribe(
      (ok) =>
        ok &&
        this.run(
          this.api.update(user.id, { name: user.name, active: !user.active }),
          user.active ? 'Compte désactivé' : 'Compte réactivé',
        ),
    );
  }

  private run(request: Observable<unknown>, success: string, after?: () => void): void {
    request.subscribe({
      next: () => {
        this.done(success);
        after?.();
      },
      error: (e: HttpErrorResponse) => this.snack.open(apiMessage(e), 'OK', { duration: 5000 }),
    });
  }

  private done(message: string): void {
    this.snack.open(message, undefined, { duration: 2500 });
    this.load();
  }

  private load(): void {
    this.api.list().subscribe((users) => this.rows.set(users));
  }
}
