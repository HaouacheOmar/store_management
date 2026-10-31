import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Observable } from 'rxjs';
import { apiMessage } from '../../core/api-error';
import { CatalogApi, Category } from '../../core/api/catalog.api';
import { AuthService } from '../../core/auth/auth.service';
import { confirmDialog } from '../../shared/confirm-dialog';

@Component({
  selector: 'app-categories',
  imports: [
    ReactiveFormsModule,
    MatTableModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
  ],
  template: `
    <header class="page-header"><h1>Catégories</h1></header>

    @if (canWrite) {
      <form class="filters" [formGroup]="addForm" (ngSubmit)="add()">
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>Nouvelle catégorie</mat-label>
          <input matInput formControlName="name" maxlength="80" />
        </mat-form-field>
        <button mat-flat-button type="submit" [disabled]="addForm.invalid">
          <mat-icon>add</mat-icon>Ajouter
        </button>
      </form>
    }

    <div class="table-wrap">
      <table mat-table [dataSource]="rows()">
        <ng-container matColumnDef="name">
          <th mat-header-cell *matHeaderCellDef>Nom</th>
          <td mat-cell *matCellDef="let c">
            @if (editingId() === c.id) {
              <mat-form-field appearance="outline" subscriptSizing="dynamic" class="inline-edit">
                <input matInput [formControl]="editName" maxlength="80" aria-label="Nouveau nom"
                       (keydown.enter)="saveRename(c)" (keydown.escape)="editingId.set(null)" />
              </mat-form-field>
            } @else {
              {{ c.name }}
            }
          </td>
        </ng-container>
        <ng-container matColumnDef="count">
          <th mat-header-cell *matHeaderCellDef class="num">Produits</th>
          <td mat-cell *matCellDef="let c" class="num">{{ c.productCount }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let c" class="actions">
            @if (editingId() === c.id) {
              <button mat-icon-button (click)="saveRename(c)" aria-label="Enregistrer"><mat-icon>check</mat-icon></button>
              <button mat-icon-button (click)="editingId.set(null)" aria-label="Annuler"><mat-icon>close</mat-icon></button>
            } @else {
              <button mat-icon-button (click)="startRename(c)" [attr.aria-label]="'Renommer ' + c.name"><mat-icon>edit</mat-icon></button>
              <!-- span : un bouton désactivé n'affiche pas de tooltip -->
              <span [matTooltip]="c.productCount > 0 ? 'Videz la catégorie avant de la supprimer' : ''">
                <button mat-icon-button (click)="remove(c)" [disabled]="c.productCount > 0"
                        [attr.aria-label]="'Supprimer ' + c.name"><mat-icon>delete</mat-icon></button>
              </span>
            }
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
      @if (loaded() && rows().length === 0) {
        <p class="empty">Aucune catégorie. Créez-en une pour ajouter des produits.</p>
      }
    </div>
  `,
  styles: `.inline-edit { width: 100%; max-width: 320px; }`,
})
export class Categories {
  private readonly api = inject(CatalogApi);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);

  protected readonly canWrite = inject(AuthService).can('CATEGORY_WRITE');
  protected readonly columns = ['name', 'count'].concat(this.canWrite ? ['actions'] : []);
  protected readonly rows = signal<Category[]>([]);
  protected readonly loaded = signal(false);
  protected readonly editingId = signal<number | null>(null);
  protected readonly addForm = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: Validators.required }),
  });
  protected readonly editName = new FormControl('', { nonNullable: true, validators: Validators.required });

  constructor() {
    this.load();
  }

  add(): void {
    this.run(this.api.createCategory(this.addForm.getRawValue().name.trim()), 'Catégorie ajoutée', () =>
      this.addForm.reset(),
    );
  }

  startRename(c: Category): void {
    this.editName.setValue(c.name);
    this.editingId.set(c.id);
  }

  saveRename(c: Category): void {
    if (this.editName.invalid) return;
    this.run(this.api.renameCategory(c.id, this.editName.value.trim()), 'Catégorie renommée', () =>
      this.editingId.set(null),
    );
  }

  remove(c: Category): void {
    confirmDialog(this.dialog, {
      title: 'Supprimer la catégorie',
      message: `Supprimer « ${c.name} » ?`,
      action: 'Supprimer',
    }).subscribe((ok) => ok && this.run(this.api.deleteCategory(c.id), 'Catégorie supprimée'));
  }

  private load(): void {
    this.api.categories().subscribe((c) => {
      this.rows.set(c);
      this.loaded.set(true);
    });
  }

  private run(request: Observable<unknown>, success: string, after?: () => void): void {
    request.subscribe({
      next: () => {
        after?.();
        this.snack.open(success, undefined, { duration: 2500 });
        this.load();
      },
      error: (e: HttpErrorResponse) => this.snack.open(apiMessage(e), 'OK', { duration: 5000 }),
    });
  }
}
