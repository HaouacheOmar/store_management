package com.store.supplier.repository;

import com.store.supplier.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    List<Supplier> findByStoreIdAndDeletedFalseOrderByNameAsc(long storeId);

    Optional<Supplier> findByIdAndStoreIdAndDeletedFalse(long id, long storeId);

    boolean existsByStoreIdAndNameIgnoreCaseAndDeletedFalseAndIdNot(long storeId, String name, long excludedId);
}
