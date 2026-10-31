package com.store.catalog.repository;

import com.store.catalog.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findByIdAndStoreIdAndDeletedFalse(long id, long storeId);

    boolean existsByCategoryIdAndDeletedFalse(long categoryId);

    boolean existsByStoreIdAndCodeIgnoreCaseAndDeletedFalseAndIdNot(long storeId, String code, long excludedId);

    /** [categoryId, nombre de produits actifs] */
    @Query("select p.category.id, count(p) from Product p where p.storeId = :storeId and p.deleted = false group by p.category.id")
    List<Object[]> countActiveByCategory(long storeId);
}
