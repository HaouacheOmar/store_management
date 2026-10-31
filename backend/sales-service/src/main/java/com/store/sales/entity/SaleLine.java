package com.store.sales.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

/** Ligne de ticket : nom, catégorie, prix et remise copiés au moment de la vente (ne bougent plus ensuite). */
@Entity
@Table(name = "sale_line")
public class SaleLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "sale_id")
    private Sale sale;

    private long productId;
    private String productName;
    private long categoryId;
    private String categoryName;
    private int qty;
    private BigDecimal unitPrice;
    private short discountPercent;
    private BigDecimal lineTotal;

    protected SaleLine() {
    }

    /** lineTotal = prix unitaire remisé (arrondi au centime par le catalogue) × quantité. */
    public SaleLine(Sale sale, long productId, String productName, long categoryId, String categoryName, int qty,
                    BigDecimal unitPrice, int discountPercent, BigDecimal finalUnitPrice) {
        this.sale = sale;
        this.productId = productId;
        this.productName = productName;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.qty = qty;
        this.unitPrice = unitPrice;
        this.discountPercent = (short) discountPercent;
        this.lineTotal = finalUnitPrice.multiply(BigDecimal.valueOf(qty));
    }

    public long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public long getCategoryId() {
        return categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public int getQty() {
        return qty;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getDiscountPercent() {
        return discountPercent;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }
}
