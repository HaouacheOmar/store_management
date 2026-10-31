import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = () =>
  inject(AuthService).isLoggedIn() || inject(Router).createUrlTree(['/login']);

/** Login / inscription : inutile si déjà connecté. */
export const anonymousGuard: CanActivateFn = () =>
  !inject(AuthService).isLoggedIn() || inject(Router).createUrlTree(['/']);

/** Écran réservé à une permission ; l'API la vérifie de toute façon. */
export const permissionGuard =
  (permission: string): CanActivateFn =>
  () =>
    inject(AuthService).can(permission) || inject(Router).createUrlTree(['/']);
