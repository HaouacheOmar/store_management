package com.store.sales.repository;

import com.store.sales.entity.Sale;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    @EntityGraph(attributePaths = "lines")
    List<Sale> findByStoreIdAndSaleDateOrderByCreatedAtDesc(long storeId, LocalDate saleDate);

    @EntityGraph(attributePaths = "lines")
    List<Sale> findByStoreIdAndSaleDateAndCashierIdOrderByCreatedAtDesc(long storeId, LocalDate saleDate, long cashierId);

    @EntityGraph(attributePaths = "lines")
    Optional<Sale> findByIdAndStoreId(long id, long storeId);

    /** Passage COMPLETED -> CANCELLED atomique : deux annulations simultanées, une seule réussit. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Sale s set s.status = com.store.sales.entity.Sale.Status.CANCELLED, s.cancelledBy = :by, s.cancelledAt = :at "
            + "where s.id = :id and s.storeId = :storeId and s.status = com.store.sales.entity.Sale.Status.COMPLETED")
    int cancel(long id, long storeId, long by, Instant at);
}
