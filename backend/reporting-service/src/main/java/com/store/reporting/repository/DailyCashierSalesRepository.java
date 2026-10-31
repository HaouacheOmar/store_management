package com.store.reporting.repository;

import com.store.reporting.entity.DailyCashierSales;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface DailyCashierSalesRepository extends JpaRepository<DailyCashierSales, DailyCashierSales.Key> {

    /** Ajout atomique, même raison que DailyProductSalesRepository.add. */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO daily_cashier_sales AS d (store_id, sale_date, cashier_id, cashier_name, ticket_count, total)
            VALUES (:storeId, :saleDate, :cashierId, :cashierName, :tickets, :total)
            ON CONFLICT (store_id, sale_date, cashier_id) DO UPDATE SET
                ticket_count = d.ticket_count + EXCLUDED.ticket_count, total = d.total + EXCLUDED.total,
                cashier_name = EXCLUDED.cashier_name""")
    void add(long storeId, LocalDate saleDate, long cashierId, String cashierName, int tickets, BigDecimal total);

    List<DailyCashierSales> findByIdStoreIdAndIdSaleDateOrderByTotalDesc(long storeId, LocalDate saleDate);

    List<DailyCashierSales> findByIdStoreIdAndIdSaleDateBetween(long storeId, LocalDate from, LocalDate to);
}
