import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { debounceTime, distinctUntilChanged, filter, switchMap } from 'rxjs';
import { apiMessage } from '../../core/api-error';
import { CatalogApi, Category, Discount, DiscountTarget, Product } from '../../core/api/catalog.api';

/** 'YYYY-MM-DDTHH:mm' (input datetime-local, heure locale) <-> ISO UTC de l'API. */
function toLocalInput(iso: string): string {
  const d = new Date(iso);
  return new Date(d.getTime() - d.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
}

@Component({
  selector: 'app-discount-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatAutocompleteModule,
    MatButtonToggleModule,
    MatButtonModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ discount ? 'Modifier la remise' : 'Nouvelle remise' }}</h2>
    <mat-dialog-content>
      <form id="discount-form" class="form-grid" [formGroup]="form" (ngSubmit)="save()">
        <mat-form-field appearance="outline">
          <mat-label>Libellé</mat-label>
          <input matInput formControlName="label" placeholder="Soldes d'été" cdkFocusInitial />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Remise</mat-label>
          <input matInput type="number" min="1" max="100" step="1" formControlName="percent" />
          <span matTextSuffix>%</span>
          @if (form.controls.percent.invalid) {
            <mat-error>Entre 1 et 100</mat-error>
          }
        </mat-form-field>

        <mat-button-toggle-group class="full target-toggle" formControlName="targetType" aria-label="Cible de la remise">
          <mat-button-toggle value="PRODUCT">Un produit</mat-button-toggle>
          <mat-button-toggle value="CATEGORY">Une catégorie</mat-button-toggle>
        </mat-button-toggle-group>

        @if (form.controls.targetType.value === 'CATEGORY') {
          <mat-form-field appearance="outline" class="full">
            <mat-label>Catégorie</mat-label>
            <mat-select formControlName="targetId">
              @for (c of categories(); track c.id) {
                <mat-option [value]="c.id">{{ c.name }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        } @else {
          <mat-form-field appearance="outline" class="full">
            <mat-label>Produit</mat-label>
            <input matInput [formControl]="productSearch" [matAutocomplete]="auto" placeholder="Nom ou code" />
            <mat-autocomplete #auto="matAutocomplete" (optionSelected)="pickProduct($event.option.value)"
                              [displayWith]="productName">
              @for (p of productOptions(); track p.id) {
                <mat-option [value]="p">{{ p.name }}</mat-option>
              }
            </mat-autocomplete>
          </mat-form-field>
        }

        <mat-form-field appearance="outline">
          <mat-label>Début</mat-label>
          <input matInput type="datetime-local" formControlName="startsAt" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Fin</mat-label>
          <input matInput type="datetime-local" formControlName="endsAt" />
        </mat-form-field>
      </form>
      @if (error()) {
        <p class="form-error" role="alert">{{ error() }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Annuler</button>
      <button mat-flat-button type="submit" form="discount-form" [disabled]="saving()">Enregistrer</button>
    </mat-dialog-actions>
  `,
  styles: `.target-toggle { margin-bottom: 16px; }`,
})
export class DiscountDialog {
  protected readonly discount = inject<Discount | null>(MAT_DIALOG_DATA);
  private readonly api = inject(CatalogApi);
  private readonly ref = inject(MatDialogRef<DiscountDialog, boolean>);
  protected readonly categories = signal<Category[]>([]);
  protected readonly productOptions = signal<Product[]>([]);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  private readonly now = new Date().toISOString();
  protected readonly form = inject(NonNullableFormBuilder).group({
    label: [this.discount?.label ?? '', [Validators.required, Validators.maxLength(120)]],
    percent: [this.discount?.percent ?? 10, [Validators.required, Validators.min(1), Validators.max(100)]],
    targetType: [this.discount?.targetType ?? ('CATEGORY' as DiscountTarget)],
    targetId: [this.discount?.targetId ?? (null as number | null), Validators.required],
    startsAt: [toLocalInput(this.discount?.startsAt ?? this.now), Validators.required],
    endsAt: [
      toLocalInput(this.discount?.endsAt ?? new Date(Date.now() + 7 * 86_400_000).toISOString()),
      Validators.required,
    ],
  });
  /** texte tapé, ou produit choisi dans l'autocomplete */
  protected readonly productSearch = new FormControl<string | Product>(
    this.discount?.targetType === 'PRODUCT' ? (this.discount.targetName ?? '') : '',
  );

  constructor() {
    this.api.categories().subscribe((c) => this.categories.set(c));
    this.form.controls.targetType.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.form.controls.targetId.setValue(null);
      this.productSearch.setValue('');
    });
    this.productSearch.valueChanges
      .pipe(
        filter((v): v is string => typeof v === 'string'),
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((q) => this.api.products({ q, categoryId: null, stock: 'ALL', page: 0, size: 10 })),
        takeUntilDestroyed(),
      )
      .subscribe((page) => this.productOptions.set(page.content));
  }

  protected productName(value: Product | string | null): string {
    return typeof value === 'string' ? value : (value?.name ?? '');
  }

  pickProduct(product: Product): void {
    this.form.controls.targetId.setValue(product.id);
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      if (this.form.controls.targetId.invalid) this.error.set('Choisissez la cible de la remise');
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    const v = this.form.getRawValue();
    const input = {
      ...v,
      targetId: v.targetId!,
      startsAt: new Date(v.startsAt).toISOString(),
      endsAt: new Date(v.endsAt).toISOString(),
    };
    const request = this.discount
      ? this.api.updateDiscount(this.discount.id, input)
      : this.api.createDiscount(input);
    request.subscribe({
      next: () => this.ref.close(true),
      error: (e: HttpErrorResponse) => {
        this.error.set(apiMessage(e));
        this.saving.set(false);
      },
    });
  }
}
