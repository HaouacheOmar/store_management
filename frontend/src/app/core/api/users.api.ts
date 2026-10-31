import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Role } from '../auth/auth.service';

export interface User {
  id: number;
  name: string;
  email: string;
  role: Role;
  active: boolean;
  permissions: string[];
}

/** Gestion des comptes du store (permission USER_MANAGE). */
@Injectable({ providedIn: 'root' })
export class UsersApi {
  private readonly http = inject(HttpClient);

  list() {
    return this.http.get<User[]>('/api/auth/users');
  }

  /** Catalogue des permissions attribuables. */
  permissions() {
    return this.http.get<string[]>('/api/auth/permissions');
  }

  /** Nouveau compte = GUEST (caissier) avec SALE_CREATE. */
  create(input: { name: string; email: string; password: string }) {
    return this.http.post<User>('/api/auth/users', input);
  }

  update(id: number, input: { name: string; active: boolean }) {
    return this.http.put<User>(`/api/auth/users/${id}`, input);
  }

  changeRole(id: number, role: 'GUEST' | 'MANAGER') {
    return this.http.put<User>(`/api/auth/users/${id}/role`, { role });
  }

  setPermissions(id: number, permissions: string[]) {
    return this.http.put<User>(`/api/auth/users/${id}/permissions`, { permissions });
  }
}
