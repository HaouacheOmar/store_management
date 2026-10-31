import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

export type Role = 'ADMIN' | 'MANAGER' | 'GUEST';

/** Contenu du JWT émis par auth-service. */
export interface Session {
  userId: number;
  storeId: number;
  storeName: string;
  currency: string;
  tz: string;
  name: string;
  role: Role;
  permissions: string[];
  exp: number;
}

export interface RegisterForm {
  storeName: string;
  name: string;
  email: string;
  password: string;
}

const TOKEN_KEY = 'store.token';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly token = signal<string | null>(localStorage.getItem(TOKEN_KEY));

  readonly session = computed(() => {
    const token = this.token();
    return token ? decode(token) : null;
  });

  getToken(): string | null {
    return this.token();
  }

  isLoggedIn(): boolean {
    const session = this.session();
    return !!session && session.exp * 1000 > Date.now();
  }

  /** Affichage uniquement : le serveur reste seul juge des permissions. */
  can(permission: string): boolean {
    return this.session()?.permissions.includes(permission) ?? false;
  }

  login(email: string, password: string): Observable<unknown> {
    return this.http
      .post<{ token: string }>('/api/auth/login', { email, password })
      .pipe(tap((r) => this.save(r.token)));
  }

  register(form: RegisterForm): Observable<unknown> {
    const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone;
    return this.http
      .post<{ token: string }>('/api/auth/register', { ...form, timezone })
      .pipe(tap((r) => this.save(r.token)));
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    this.token.set(null);
    this.router.navigate(['/login']);
  }

  private save(token: string): void {
    localStorage.setItem(TOKEN_KEY, token);
    this.token.set(token);
  }
}

function decode(token: string): Session | null {
  try {
    const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const claims = JSON.parse(
      new TextDecoder().decode(Uint8Array.from(atob(base64), (c) => c.charCodeAt(0))),
    );
    return { ...claims, userId: Number(claims.sub) };
  } catch {
    return null;
  }
}
