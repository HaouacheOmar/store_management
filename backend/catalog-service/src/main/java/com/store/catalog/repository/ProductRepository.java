package com.store.catalog.repository;

import com.store.catalog.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findByIdAndStoreIdAndDeletedFalse(long id, long storeId);

    boolean existsByCategoryIdAndDeletedFalse(long categoryId);

    boolean existsByStoreIdAndCodeIgnoreCaseAndDeletedFalseAndIdNot(long storeId, String code, long excludedId);

    /** Incrément atomique : jamais de lecture-modification-écriture du stock en Java. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Product p set p.stockQty = p.stockQty + :qty, p.updatedAt = :now where p.id = :id and p.storeId = :storeId and p.deleted = false")
    int addStock(long id, long storeId, int qty, Instant now);

    @Query("select p.stockQty from Product p where p.id = :id")
    int stockOf(long id);

    /** [categoryId, nombre de produits actifs] */
    @Query("select p.category.id, count(p) from Product p where p.storeId = :storeId and p.deleted = false group by p.category.id")
    List<Object[]> countActiveByCategory(long storeId);
}
