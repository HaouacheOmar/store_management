import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

export interface Category {
  id: number;
  name: string;
  productCount: number;
}

export interface Product {
  id: number;
  name: string;
  code: string | null;
  categoryId: number;
  categoryName: string;
  price: number;
  /** remise active la plus forte (0 si aucune) et prix après remise */
  discountPercent: number;
  finalPrice: number;
  stockQty: number;
  alertThreshold: number;
  version: number;
}

export interface ProductInput {
  name: string;
  code: string | null;
  categoryId: number;
  price: number;
  stockQty: number;
  alertThreshold: number;
  version?: number;
}

/** Page Spring Data (sérialisation VIA_DTO). */
export interface Page<T> {
  content: T[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
}

/** LOW = à restocker (stock ≤ seuil, ruptures comprises) ; OUT = rupture. */
export type StockFilter = 'ALL' | 'LOW' | 'OUT';

export interface ProductQuery {
  q: string;
  categoryId: number | null;
  stock: StockFilter;
  page: number;
  size: number;
}

export interface Restock {
  id: number;
  productId: number;
  productName: string;
  supplierId: number;
  supplierName: string;
  qty: number;
  unitCost: number | null;
  userName: string;
  createdAt: string;
}

export interface RestockInput {
  productId: number;
  supplierId: number;
  qty: number;
  unitCost: number | null;
}

/** from / to : 'YYYY-MM-DD', bornes incluses (jours du store). */
export interface RestockQuery {
  supplierId: number | null;
  from: string;
  to: string;
  page: number;
  size: number;
}

export type DiscountTarget = 'PRODUCT' | 'CATEGORY';
export type DiscountStatus = 'ACTIVE' | 'UPCOMING' | 'PAST';

export interface Discount {
  id: number;
  label: string;
  percent: number;
  targetType: DiscountTarget;
  targetId: number;
  targetName: string | null;
  startsAt: string;
  endsAt: string;
  status: DiscountStatus;
}

export interface DiscountInput {
  label: string;
  percent: number;
  targetType: DiscountTarget;
  targetId: number;
  startsAt: string;
  endsAt: string;
}

@Injectable({ providedIn: 'root' })
export class CatalogApi {
  private readonly http = inject(HttpClient);

  categories() {
    return this.http.get<Category[]>('/api/catalog/categories');
  }

  createCategory(name: string) {
    return this.http.post<Category>('/api/catalog/categories', { name });
  }

  renameCategory(id: number, name: string) {
    return this.http.put<void>(`/api/catalog/categories/${id}`, { name });
  }

  deleteCategory(id: number) {
    return this.http.delete<void>(`/api/catalog/categories/${id}`);
  }

  products(query: ProductQuery) {
    const params: Record<string, string | number> = {
      stock: query.stock,
      page: query.page,
      size: query.size,
    };
    if (query.q.trim()) params['q'] = query.q.trim();
    if (query.categoryId !== null) params['categoryId'] = query.categoryId;
    return this.http.get<Page<Product>>('/api/catalog/products', { params });
  }

  createProduct(input: ProductInput) {
    return this.http.post<Product>('/api/catalog/products', input);
  }

  updateProduct(id: number, input: ProductInput) {
    return this.http.put<Product>(`/api/catalog/products/${id}`, input);
  }

  deleteProduct(id: number) {
    return this.http.delete<void>(`/api/catalog/products/${id}`);
  }

  restocks(query: RestockQuery) {
    const params: Record<string, string | number> = { page: query.page, size: query.size };
    if (query.supplierId !== null) params['supplierId'] = query.supplierId;
    if (query.from) params['from'] = query.from;
    if (query.to) params['to'] = query.to;
    return this.http.get<Page<Restock>>('/api/catalog/restocks', { params });
  }

  restock(input: RestockInput) {
    return this.http.post<Restock>('/api/catalog/restocks', input);
  }

  discounts(status: DiscountStatus) {
    return this.http.get<Discount[]>('/api/catalog/discounts', { params: { status } });
  }

  createDiscount(input: DiscountInput) {
    return this.http.post<Discount>('/api/catalog/discounts', input);
  }

  updateDiscount(id: number, input: DiscountInput) {
    return this.http.put<Discount>(`/api/catalog/discounts/${id}`, input);
  }

  deleteDiscount(id: number) {
    return this.http.delete<void>(`/api/catalog/discounts/${id}`);
  }

  endDiscount(id: number) {
    return this.http.post<Discount>(`/api/catalog/discounts/${id}/end`, {});
  }
}
