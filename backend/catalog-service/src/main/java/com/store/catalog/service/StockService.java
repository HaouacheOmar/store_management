package com.store.catalog.service;

import com.store.catalog.entity.Discount;
import com.store.catalog.entity.Product;
import com.store.catalog.entity.StockReservation;
import com.store.catalog.repository.ProductRepository;
import com.store.catalog.repository.StockReservationRepository;
import com.store.common.contract.StockContract.Line;
import com.store.common.contract.StockContract.PricedLine;
import com.store.common.contract.StockContract.Reservation;
import com.store.common.events.Events;
import com.store.common.events.Events.StockAlert;
import com.store.common.events.Events.StockLowEvent;
import com.store.common.events.Events.StockRestockedEvent;
import com.store.common.outbox.OutboxPublisher;
import com.store.common.security.CurrentUser;
import com.store.common.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Mouvements de stock liés aux ventes. Toujours par UPDATE atomique, jamais lecture-modification-écriture. */
@Service
public class StockService {

    /** Une compensation suit immédiatement l'échec de la vente : au-delà, la sortie de stock est définitive. */
    static final Duration RELEASE_WINDOW = Duration.ofMinutes(5);

    private final ProductRepository products;
    private final StockReservationRepository reservations;
    private final DiscountService discounts;
    private final OutboxPublisher outbox;

    public StockService(ProductRepository products, StockReservationRepository reservations,
                        DiscountService discounts, OutboxPublisher outbox) {
        this.products = products;
        this.reservations = reservations;
        this.discounts = discounts;
        this.outbox = outbox;
    }

    /**
     * Sort du stock les produits d'une vente et renvoie les prix faisant foi (remise active la plus forte),
     * avec l'id de réservation qui seul permet de rendre ce stock. Tout ou rien : un seul produit en stock
     * insuffisant annule la transaction entière.
     */
    @Transactional
    public Reservation decrement(List<Line> lines) {
        var me = CurrentUser.get();
        long storeId = me.storeId();
        Map<Long, Integer> merged = merge(lines);
        List<Discount> active = discounts.active();
        List<PricedLine> priced = new ArrayList<>();
        // un produit présent sur plusieurs lignes = une seule sortie ; ordre par id = pas d'interblocage entre deux ventes
        for (Map.Entry<Long, Integer> entry : merged.entrySet()) {
            Product p = products.findByIdAndStoreIdAndDeletedFalse(entry.getKey(), storeId)
                    .orElseThrow(() -> ApiException.notFound("Produit"));
            int qty = entry.getValue();
            if (products.removeStock(p.getId(), storeId, qty, Instant.now()) == 0) {
                int available = products.stockOf(p.getId());
                throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK",
                        "Stock insuffisant pour « %s » (disponible : %d)".formatted(p.getName(), available),
                        Map.of("productId", p.getId(), "available", available));
            }
            int left = products.stockOf(p.getId());
            if (left <= p.getAlertThreshold()) {
                outbox.publish(Events.STOCK_LOW, storeId, new StockLowEvent(storeId, p.getId(), p.getName(), left,
                        p.getAlertThreshold(), left == 0 ? StockAlert.OUT : StockAlert.LOW));
            }
            int percent = DiscountService.percentFor(p, active);
            priced.add(new PricedLine(p.getId(), p.getName(), p.getCategory().getId(), p.getCategory().getName(),
                    p.getPrice(), percent, DiscountService.discountedPrice(p.getPrice(), percent)));
        }
        StockReservation reservation = reservations.save(new StockReservation(storeId, me.userId(),
                merged.entrySet().stream().map(e -> new StockReservation.Line(e.getKey(), e.getValue())).toList()));
        return new Reservation(reservation.getId(), priced);
    }

    /**
     * Compensation d'une vente non enregistrée : rend exactement ce que la réservation a sorti,
     * une seule fois, si l'appelant en est l'auteur et dans le délai. Sinon 409.
     */
    @Transactional
    public void release(UUID reservationId) {
        var me = CurrentUser.get();
        Instant now = Instant.now();
        if (reservations.release(reservationId, me.storeId(), me.userId(), now.minus(RELEASE_WINDOW), now) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "RESERVATION_NOT_RELEASABLE",
                    "Réservation inconnue, déjà rendue ou expirée");
        }
        StockReservation reservation = reservations.findById(reservationId).orElseThrow();
        increment(reservation.getStoreId(), reservation.getLines().stream()
                .map(l -> new Line(l.productId(), l.qty())).toList());
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeReservations() {
        reservations.purgeOlderThan(Instant.now().minus(Duration.ofDays(1)));
    }

    /** Recrédit (vente annulée via Kafka, ou release d'une réservation). Un produit supprimé depuis est ignoré. */
    @Transactional
    public void increment(long storeId, List<Line> lines) {
        for (Map.Entry<Long, Integer> entry : merge(lines).entrySet()) {
            long id = entry.getKey();
            if (products.addStock(id, storeId, entry.getValue(), Instant.now()) == 0) continue;
            Product p = products.findById(id).orElseThrow();
            outbox.publish(Events.STOCK_RESTOCKED, storeId,
                    new StockRestockedEvent(storeId, id, p.getStockQty(), p.getAlertThreshold()));
        }
    }

    private static Map<Long, Integer> merge(List<Line> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "La vente est vide");
        }
        Map<Long, Integer> merged = new TreeMap<>();
        for (Line line : lines) {
            if (line.qty() <= 0) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Quantité invalide");
            merged.merge(line.productId(), line.qty(), Integer::sum);
        }
        return merged;
    }
}
