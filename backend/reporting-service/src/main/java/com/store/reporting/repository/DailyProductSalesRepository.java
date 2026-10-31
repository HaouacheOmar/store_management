package com.store.reporting.repository;

import com.store.reporting.entity.DailyProductSales;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface DailyProductSalesRepository extends JpaRepository<DailyProductSales, DailyProductSales.Key> {

    /**
     * Ajout atomique (quantité et montant signés : négatifs pour une annulation). Requête native
     * INSERT … ON CONFLICT : un lecture-modification-écriture en JPA perdrait des ventes entre deux instances.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO daily_product_sales AS d
                (store_id, sale_date, product_id, product_name, category_id, category_name, qty, revenue)
            VALUES (:storeId, :saleDate, :productId, :productName, :categoryId, :categoryName, :qty, :revenue)
            ON CONFLICT (store_id, sale_date, product_id) DO UPDATE SET
                qty = d.qty + EXCLUDED.qty, revenue = d.revenue + EXCLUDED.revenue,
                product_name = EXCLUDED.product_name, category_id = EXCLUDED.category_id,
                category_name = EXCLUDED.category_name""")
    void add(long storeId, LocalDate saleDate, long productId, String productName, long categoryId,
             String categoryName, int qty, BigDecimal revenue);

    List<DailyProductSales> findByIdStoreIdAndIdSaleDateAndQtyGreaterThanOrderByQtyDescProductNameAsc(
            long storeId, LocalDate saleDate, int minQty);

    @Query("""
            select coalesce(sum(d.qty), 0) from DailyProductSales d
            where d.id.storeId = :storeId and d.id.saleDate between :from and :to""")
    long itemsSold(long storeId, LocalDate from, LocalDate to);

    /** Tri et nombre de lignes portés par le Pageable (cf. ReportingController.ranking). */
    @Query("""
            select new com.store.reporting.repository.RankRow(d.id.productId, max(d.productName), sum(d.qty), sum(d.revenue))
            from DailyProductSales d
            where d.id.storeId = :storeId and d.id.saleDate between :from and :to
              and (:categoryId is null or d.categoryId = :categoryId)
            group by d.id.productId having sum(d.qty) > 0""")
    List<RankRow> rankProducts(long storeId, LocalDate from, LocalDate to, Long categoryId, Pageable page);

    @Query("""
            select new com.store.reporting.repository.RankRow(d.categoryId, max(d.categoryName), sum(d.qty), sum(d.revenue))
            from DailyProductSales d
            where d.id.storeId = :storeId and d.id.saleDate between :from and :to
            group by d.categoryId having sum(d.qty) > 0""")
    List<RankRow> rankCategories(long storeId, LocalDate from, LocalDate to, Pageable page);
}
