package com.store.auth.entity;

/**
 * Catalogue des permissions (cf. PRD §2.1). Une nouvelle fonctionnalité sensible = une nouvelle valeur ici ;
 * les autres services la vérifient par son nom : @PreAuthorize("hasAuthority('...')").
 */
public enum Permission {
    SALE_CREATE,
    SALE_CANCEL,
    SALES_VIEW_ALL,
    PRODUCT_WRITE,
    CATEGORY_WRITE,
    STOCK_RESTOCK,
    SUPPLIER_WRITE,
    DISCOUNT_WRITE,
    STATS_VIEW,
    USER_MANAGE
}
