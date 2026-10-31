package com.store.reporting.repository;

import java.math.BigDecimal;

/** Ligne de classement (produit ou catégorie) : projection JPQL d'un GROUP BY. */
public record RankRow(Long id, String name, Long qty, BigDecimal revenue) {
}
