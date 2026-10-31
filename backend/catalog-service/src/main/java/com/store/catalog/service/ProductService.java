package com.store.catalog.service;

import com.store.catalog.dto.Dtos.ProductRequest;
import com.store.catalog.dto.Dtos.StockFilter;
import com.store.catalog.entity.Category;
import com.store.catalog.entity.Product;
import com.store.catalog.repository.ProductRepository;
import com.store.common.security.CurrentUser;
import com.store.common.web.ApiException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class ProductService {

    private final ProductRepository products;
    private final CategoryService categories;

    public ProductService(ProductRepository products, CategoryService categories) {
        this.products = products;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public Page<Product> search(Long categoryId, String q, StockFilter stock, Pageable page) {
        long storeId = CurrentUser.get().storeId();
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("storeId"), storeId));
            where.add(cb.isFalse(root.get("deleted")));
            if (categoryId != null) where.add(cb.equal(root.get("category").get("id"), categoryId));
            if (q != null && !q.isBlank()) {
                String text = q.trim().toLowerCase(Locale.ROOT);
                where.add(cb.or(
                        cb.like(cb.lower(root.get("name")), "%" + text.replace("%", "\\%").replace("_", "\\_") + "%", '\\'),
                        cb.equal(cb.lower(root.get("code")), text)));
            }
            if (stock == StockFilter.LOW) where.add(cb.le(root.get("stockQty"), root.get("alertThreshold")));
            if (stock == StockFilter.OUT) where.add(cb.equal(root.get("stockQty"), 0));
            return cb.and(where.toArray(Predicate[]::new));
        };
        return products.findAll(spec, page);
    }

    @Transactional(readOnly = true)
    public Product get(long id) {
        return products.findByIdAndStoreIdAndDeletedFalse(id, CurrentUser.get().storeId())
                .orElseThrow(() -> ApiException.notFound("Produit"));
    }

    public Product create(ProductRequest req) {
        long storeId = CurrentUser.get().storeId();
        Category category = categories.get(req.categoryId());
        String code = blankToNull(req.code());
        checkCodeFree(storeId, code, 0);
        return products.save(new Product(storeId, category, req.name().trim(), code, req.price(),
                req.stockQty(), req.alertThreshold()));
    }

    /** version : celle lue par le client ; si le produit a changé entre-temps, on refuse plutôt que d'écraser. */
    public Product update(long id, ProductRequest req) {
        Product product = get(id);
        if (req.version() == null || req.version() != product.getVersion()) {
            throw new ApiException(HttpStatus.CONFLICT, "STALE_PRODUCT",
                    "Le produit a été modifié entre-temps, rechargez la page");
        }
        String code = blankToNull(req.code());
        checkCodeFree(CurrentUser.get().storeId(), code, id);
        product.update(categories.get(req.categoryId()), req.name().trim(), code, req.price(), req.alertThreshold());
        products.flush(); // incrémente version maintenant, pour la renvoyer au client
        return product;
    }

    public void delete(long id) {
        get(id).delete();
    }

    private void checkCodeFree(long storeId, String code, long excludedId) {
        if (code != null && products.existsByStoreIdAndCodeIgnoreCaseAndDeletedFalseAndIdNot(storeId, code, excludedId)) {
            throw new ApiException(HttpStatus.CONFLICT, "CODE_TAKEN", "Un produit porte déjà ce code");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
