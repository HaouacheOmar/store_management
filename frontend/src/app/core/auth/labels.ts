import { Role } from './auth.service';

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Administrateur',
  MANAGER: 'Responsable',
  GUEST: 'Caissier',
};

/** Libellés du catalogue de permissions (PRD §2.1). */
export const PERMISSION_LABELS: Partial<Record<string, string>> = {
  SALE_CREATE: 'Encaisser',
  SALE_CANCEL: 'Annuler une vente',
  SALES_VIEW_ALL: 'Voir toutes les ventes',
  PRODUCT_WRITE: 'Gérer les produits',
  CATEGORY_WRITE: 'Gérer les catégories',
  STOCK_RESTOCK: 'Restocker',
  SUPPLIER_WRITE: 'Gérer les fournisseurs',
  DISCOUNT_WRITE: 'Gérer les remises',
  STATS_VIEW: 'Voir les statistiques',
  USER_MANAGE: 'Gérer les utilisateurs',
};
