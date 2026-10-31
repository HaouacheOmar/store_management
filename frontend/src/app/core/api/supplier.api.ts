import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

export interface Supplier {
  id: number;
  name: string;
  phone: string | null;
  email: string | null;
  address: string | null;
}

export type SupplierInput = Omit<Supplier, 'id'>;

@Injectable({ providedIn: 'root' })
export class SupplierApi {
  private readonly http = inject(HttpClient);

  list() {
    return this.http.get<Supplier[]>('/api/suppliers');
  }

  create(input: SupplierInput) {
    return this.http.post<Supplier>('/api/suppliers', input);
  }

  update(id: number, input: SupplierInput) {
    return this.http.put<Supplier>(`/api/suppliers/${id}`, input);
  }

  delete(id: number) {
    return this.http.delete<void>(`/api/suppliers/${id}`);
  }
}
