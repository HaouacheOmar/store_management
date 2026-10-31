package com.store.catalog.repository;

import com.store.catalog.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByStoreIdAndDeletedFalseOrderByNameAsc(long storeId);

    Optional<Category> findByIdAndStoreIdAndDeletedFalse(long id, long storeId);

    boolean existsByStoreIdAndNameIgnoreCaseAndDeletedFalseAndIdNot(long storeId, String name, long excludedId);
}
