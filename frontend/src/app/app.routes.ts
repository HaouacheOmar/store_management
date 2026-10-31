import { Routes } from '@angular/router';
import { anonymousGuard, authGuard, permissionGuard } from './core/auth/guards';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [anonymousGuard],
    loadComponent: () => import('./features/auth/login').then((m) => m.Login),
  },
  {
    path: 'register',
    canActivate: [anonymousGuard],
    loadComponent: () => import('./features/auth/register').then((m) => m.Register),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./core/layout/shell').then((m) => m.Shell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
      },
      // consultation ouverte à tout le store ; les écritures sont masquées sans permission et refusées par l'API
      {
        path: 'pos',
        loadComponent: () => import('./features/pos/pos').then((m) => m.Pos),
      },
      {
        path: 'sales',
        loadComponent: () => import('./features/sales/sales').then((m) => m.Sales),
      },
      {
        path: 'sales/:id',
        loadComponent: () => import('./features/sales/receipt').then((m) => m.Receipt),
      },
      {
        path: 'products',
        loadComponent: () => import('./features/products/products').then((m) => m.Products),
      },
      {
        path: 'categories',
        loadComponent: () => import('./features/categories/categories').then((m) => m.Categories),
      },
      {
        path: 'suppliers',
        loadComponent: () => import('./features/suppliers/suppliers').then((m) => m.Suppliers),
      },
      {
        path: 'restocks',
        loadComponent: () => import('./features/stock/restocks').then((m) => m.Restocks),
      },
      {
        path: 'discounts',
        loadComponent: () => import('./features/discounts/discounts').then((m) => m.Discounts),
      },
      {
        path: 'users',
        canActivate: [permissionGuard('USER_MANAGE')],
        loadComponent: () => import('./features/users/users').then((m) => m.Users),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
