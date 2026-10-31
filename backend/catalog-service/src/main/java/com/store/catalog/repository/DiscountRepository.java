package com.store.catalog.repository;

import com.store.catalog.entity.Discount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DiscountRepository extends JpaRepository<Discount, Long> {

    /** Actives : startsAt ≤ now < endsAt. */
    List<Discount> findByStoreIdAndStartsAtLessThanEqualAndEndsAtGreaterThanOrderByEndsAtAsc(long storeId, Instant now1, Instant now2);

    List<Discount> findByStoreIdAndStartsAtGreaterThanOrderByStartsAtAsc(long storeId, Instant now);

    List<Discount> findTop100ByStoreIdAndEndsAtLessThanEqualOrderByEndsAtDesc(long storeId, Instant now);

    Optional<Discount> findByIdAndStoreId(long id, long storeId);
}
