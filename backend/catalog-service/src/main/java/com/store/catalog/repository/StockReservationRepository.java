package com.store.catalog.repository;

import com.store.catalog.entity.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {

    /**
     * Passage RESERVED -> RELEASED atomique : une réservation ne se rend qu'une fois, par son auteur,
     * dans son store, avant notBefore + délai. 0 ligne = refus.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update StockReservation r
            set r.status = com.store.catalog.entity.StockReservation.Status.RELEASED, r.releasedAt = :now
            where r.id = :id and r.storeId = :storeId and r.userId = :userId and r.createdAt > :notBefore
              and r.status = com.store.catalog.entity.StockReservation.Status.RESERVED""")
    int release(UUID id, long storeId, long userId, Instant notBefore, Instant now);

    /** Les lignes partent avec (ON DELETE CASCADE en base). */
    @Modifying
    @Query("delete from StockReservation r where r.createdAt < :before")
    int purgeOlderThan(Instant before);
}
