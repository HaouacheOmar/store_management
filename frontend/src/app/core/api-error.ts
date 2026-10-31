import { HttpErrorResponse } from '@angular/common/http';

/** Message lisible depuis le format d'erreur commun {code, message, details}. */
export function apiMessage(e: HttpErrorResponse): string {
  if (e.status === 0) return 'Serveur injoignable';
  return e.error?.message ?? 'Erreur inattendue';
}
