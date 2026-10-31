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
}
