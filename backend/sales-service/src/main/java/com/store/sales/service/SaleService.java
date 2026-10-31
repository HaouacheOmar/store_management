package com.store.sales.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.common.contract.StockContract.Line;
import com.store.common.contract.StockContract.PricedLine;
import com.store.common.contract.StockContract.Reservation;
import com.store.common.contract.StockContract.StockRequest;
import com.store.common.events.Events;
import com.store.common.events.Events.SaleEvent;
import com.store.common.outbox.OutboxPublisher;
import com.store.common.security.CurrentUser;
import com.store.common.web.ApiError;
import com.store.common.web.ApiException;
import com.store.sales.client.CatalogClient;
import com.store.sales.dto.Dtos.SaleRequest;
import com.store.sales.entity.Sale;
import com.store.sales.entity.SaleLine;
import com.store.sales.repository.SaleRepository;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class SaleService {

    private static final Logger log = LoggerFactory.getLogger(SaleService.class);
    public static final String VIEW_ALL = "SALES_VIEW_ALL";

    private final SaleRepository sales;
    private final CatalogClient catalog;
    private final OutboxPublisher outbox;
    private final TransactionTemplate tx;
    private final ObjectMapper json;

    public SaleService(SaleRepository sales, CatalogClient catalog, OutboxPublisher outbox,
                       TransactionTemplate tx, ObjectMapper json) {
        this.sales = sales;
        this.catalog = catalog;
        this.outbox = outbox;
        this.tx = tx;
        this.json = json;
    }

    /**
     * Encaissement (IMPLEMENTATION §2.3) : 1) le catalogue sort le stock et fixe les prix ;
     * 2) la vente et son événement sont enregistrés ensemble. Si 2) échoue, la réservation est rendue.
     */
    public Sale create(SaleRequest req) {
        var me = CurrentUser.get();
        Map<Long, Integer> qtyByProduct = new TreeMap<>();
        req.lines().forEach(l -> qtyByProduct.merge(l.productId(), l.qty(), Integer::sum));
        var stockRequest = new StockRequest(qtyByProduct.entrySet().stream()
                .map(e -> new Line(e.getKey(), e.getValue())).toList());

        Reservation reservation = callCatalog(() -> catalog.decrement(stockRequest));

        Sale sale = new Sale(me.storeId(), me.userId(), me.name(), me.today());
        for (PricedLine p : reservation.lines()) {
            sale.addLine(new SaleLine(sale, p.productId(), p.productName(), p.categoryId(), p.categoryName(),
                    qtyByProduct.get(p.productId()), p.unitPrice(), p.discountPercent(), p.finalUnitPrice()));
        }
        if (req.cashReceived().compareTo(sale.getTotal()) < 0) {
            compensate(reservation.reservationId());
            throw new ApiException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_CASH",
                    "Montant reçu insuffisant (total : %s)".formatted(sale.getTotal()),
                    Map.of("total", sale.getTotal()));
        }
        sale.pay(req.cashReceived());

        try {
            return tx.execute(status -> {
                Sale saved = sales.save(sale);
                outbox.publish(Events.SALE_COMPLETED, me.storeId(), toEvent(saved));
                return saved;
            });
        } catch (RuntimeException e) {
            compensate(reservation.reservationId());
            throw e;
        }
    }

    /** Un caissier ne voit que ses ventes ; SALES_VIEW_ALL ouvre celles de tout le store (filtrables par caissier). */
    @Transactional(readOnly = true)
    public List<Sale> list(LocalDate date, Long cashierId) {
        var me = CurrentUser.get();
        LocalDate day = date != null ? date : me.today();
        Long cashier = CurrentUser.has(VIEW_ALL) ? cashierId : Long.valueOf(me.userId());
        return cashier == null
                ? sales.findByStoreIdAndSaleDateOrderByCreatedAtDesc(me.storeId(), day)
                : sales.findByStoreIdAndSaleDateAndCashierIdOrderByCreatedAtDesc(me.storeId(), day, cashier);
    }

    @Transactional(readOnly = true)
    public Sale get(long id) {
        var me = CurrentUser.get();
        return sales.findByIdAndStoreId(id, me.storeId())
                .filter(s -> s.getCashierId() == me.userId() || CurrentUser.has(VIEW_ALL))
                .orElseThrow(() -> ApiException.notFound("Vente"));
    }

    /** Le catalogue recrédite le stock en consommant sale.cancelled (idempotent) : pas d'appel synchrone à rater. */
    @Transactional
    public Sale cancel(long id) {
        var me = CurrentUser.get();
        if (sales.cancel(id, me.storeId(), me.userId(), Instant.now()) == 0) {
            Sale existing = sales.findByIdAndStoreId(id, me.storeId()).orElseThrow(() -> ApiException.notFound("Vente"));
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_CANCELLED",
                    "La vente n°%d est déjà annulée".formatted(existing.getId()));
        }
        Sale sale = sales.findByIdAndStoreId(id, me.storeId()).orElseThrow();
        outbox.publish(Events.SALE_CANCELLED, me.storeId(), toEvent(sale));
        return sale;
    }

    private static SaleEvent toEvent(Sale s) {
        return new SaleEvent(s.getId(), s.getStoreId(), s.getCashierId(), s.getCashierName(), s.getSaleDate(),
                s.getTotal(), s.getLines().stream().map(l -> new Events.SaleLine(l.getProductId(), l.getProductName(),
                l.getCategoryId(), l.getCategoryName(), l.getQty(), l.getUnitPrice(), l.getDiscountPercent(),
                l.getLineTotal())).toList());
    }

    /** Les erreurs métier du catalogue (stock insuffisant, produit inconnu…) sont renvoyées telles quelles au caissier. */
    private <T> T callCatalog(Supplier<T> call) {
        try {
            return call.get();
        } catch (FeignException e) {
            if (e.status() >= 400 && e.status() < 500) {
                try {
                    ApiError error = json.readValue(e.contentUTF8(), ApiError.class);
                    throw new ApiException(HttpStatus.valueOf(e.status()), error.code(), error.message(), error.details());
                } catch (com.fasterxml.jackson.core.JsonProcessingException parse) {
                    log.warn("Réponse d'erreur du catalogue illisible", parse);
                }
            }
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CATALOG_UNAVAILABLE",
                    "Catalogue indisponible, réessayez dans un instant");
        }
    }

    // ponytail: si la compensation échoue aussi, le stock reste sous-évalué (journalisé) ; une file de reprise si ça arrive en vrai
    private void compensate(UUID reservationId) {
        try {
            catalog.release(reservationId);
        } catch (RuntimeException e) {
            log.error("Compensation impossible, réservation {} à rendre manuellement", reservationId, e);
        }
    }
}
