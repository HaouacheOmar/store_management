package com.store.catalog.service;

import com.store.catalog.client.SupplierClient;
import com.store.catalog.client.SupplierClient.SupplierRef;
import com.store.catalog.dto.Dtos.RestockRequest;
import com.store.catalog.entity.Product;
import com.store.catalog.entity.Restock;
import com.store.catalog.repository.ProductRepository;
import com.store.catalog.repository.RestockRepository;
import com.store.common.events.Events;
import com.store.common.events.Events.StockRestockedEvent;
import com.store.common.outbox.OutboxPublisher;
import com.store.common.security.CurrentUser;
import com.store.common.web.ApiException;
import feign.FeignException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class RestockService {

    private final ProductService products;
    private final ProductRepository productRepo;
    private final RestockRepository restocks;
    private final SupplierClient suppliers;
    private final OutboxPublisher outbox;
    private final TransactionTemplate tx;

    public RestockService(ProductService products, ProductRepository productRepo, RestockRepository restocks,
                          SupplierClient suppliers, OutboxPublisher outbox, TransactionTemplate tx) {
        this.products = products;
        this.productRepo = productRepo;
        this.restocks = restocks;
        this.suppliers = suppliers;
        this.outbox = outbox;
        this.tx = tx;
    }

    /** L'appel HTTP au fournisseur se fait avant la transaction : on ne garde pas une connexion DB pendant un appel réseau. */
    public Restock restock(RestockRequest req) {
        var me = CurrentUser.get();
        Product product = products.get(req.productId());
        SupplierRef supplier = fetchSupplier(req.supplierId());

        return tx.execute(status -> {
            Restock restock = restocks.save(new Restock(me.storeId(), product, supplier.id(), supplier.name(),
                    req.qty(), req.unitCost(), me.userId(), me.name()));
            if (productRepo.addStock(product.getId(), me.storeId(), req.qty(), Instant.now()) == 0) {
                throw ApiException.notFound("Produit"); // supprimé entre-temps -> rollback
            }
            outbox.publish(Events.STOCK_RESTOCKED, me.storeId(), new StockRestockedEvent(me.storeId(),
                    product.getId(), productRepo.stockOf(product.getId()), product.getAlertThreshold()));
            return restock;
        });
    }

    @Transactional(readOnly = true)
    public Page<Restock> history(Long productId, Long supplierId, LocalDate from, LocalDate to, Pageable page) {
        var me = CurrentUser.get();
        Specification<Restock> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("storeId"), me.storeId()));
            if (productId != null) where.add(cb.equal(root.get("product").get("id"), productId));
            if (supplierId != null) where.add(cb.equal(root.get("supplierId"), supplierId));
            // bornes en jours du fuseau du store, fin incluse
            if (from != null) where.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay(me.zone()).toInstant()));
            if (to != null) where.add(cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay(me.zone()).toInstant()));
            return cb.and(where.toArray(Predicate[]::new));
        };
        return restocks.findAll(spec, page);
    }

    private SupplierRef fetchSupplier(long id) {
        try {
            return suppliers.get(id);
        } catch (FeignException.NotFound e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUPPLIER_NOT_FOUND", "Fournisseur introuvable");
        } catch (FeignException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SUPPLIER_UNAVAILABLE",
                    "Service fournisseurs indisponible, réessayez dans un instant");
        }
    }
}
