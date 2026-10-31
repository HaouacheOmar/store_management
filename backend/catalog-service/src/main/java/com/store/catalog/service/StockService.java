package com.store.catalog.service;

import com.store.catalog.entity.Discount;
import com.store.catalog.entity.Product;
import com.store.catalog.repository.ProductRepository;
import com.store.common.contract.StockContract.Line;
import com.store.common.contract.StockContract.PricedLine;
import com.store.common.events.Events;
import com.store.common.events.Events.StockAlert;
import com.store.common.events.Events.StockLowEvent;
import com.store.common.events.Events.StockRestockedEvent;
import com.store.common.outbox.OutboxPublisher;
import com.store.common.security.CurrentUser;
import com.store.common.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Mouvements de stock liés aux ventes. Toujours par UPDATE atomique, jamais lecture-modification-écriture. */
@Service
public class StockService {

    private final ProductRepository products;
    private final DiscountService discounts;
    private final OutboxPublisher outbox;

    public StockService(ProductRepository products, DiscountService discounts, OutboxPublisher outbox) {
        this.products = products;
        this.discounts = discounts;
        this.outbox = outbox;
    }

    /**
     * Sort du stock les produits d'une vente et renvoie les prix faisant foi (remise active la plus forte).
     * Tout ou rien : un seul produit en stock insuffisant annule la transaction entière.
     */
    @Transactional
    public List<PricedLine> decrement(List<Line> lines) {
        long storeId = CurrentUser.get().storeId();
        List<Discount> active = discounts.active();
        List<PricedLine> priced = new ArrayList<>();
        // un produit présent sur plusieurs lignes = une seule sortie ; ordre par id = pas d'interblocage entre deux ventes
        for (Map.Entry<Long, Integer> entry : merge(lines).entrySet()) {
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
        return priced;
    }

    /** Recrédit (vente annulée ou compensation d'une vente non enregistrée). Un produit supprimé depuis est ignoré. */
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
