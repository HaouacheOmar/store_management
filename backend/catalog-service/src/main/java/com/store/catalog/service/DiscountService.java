package com.store.catalog.service;

import com.store.catalog.dto.Dtos.DiscountRequest;
import com.store.catalog.dto.Dtos.DiscountResponse;
import com.store.catalog.entity.Discount;
import com.store.catalog.entity.Product;
import com.store.catalog.repository.CategoryRepository;
import com.store.catalog.repository.DiscountRepository;
import com.store.catalog.repository.ProductRepository;
import com.store.common.security.CurrentUser;
import com.store.common.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class DiscountService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final DiscountRepository discounts;
    private final ProductService products;
    private final CategoryService categories;
    private final ProductRepository productRepo;
    private final CategoryRepository categoryRepo;

    public DiscountService(DiscountRepository discounts, ProductService products, CategoryService categories,
                           ProductRepository productRepo, CategoryRepository categoryRepo) {
        this.discounts = discounts;
        this.products = products;
        this.categories = categories;
        this.productRepo = productRepo;
        this.categoryRepo = categoryRepo;
    }

    /** Remises actives du store : lues une fois, puis appliquées à chaque produit d'une page. */
    @Transactional(readOnly = true)
    public List<Discount> active() {
        Instant now = Instant.now();
        return discounts.findByStoreIdAndStartsAtLessThanEqualAndEndsAtGreaterThanOrderByEndsAtAsc(
                CurrentUser.get().storeId(), now, now);
    }

    /** Règle PRD M5 : plusieurs remises actives sur un produit (la sienne, celle de sa catégorie) -> la plus forte, sans cumul. */
    public static int percentFor(Product product, List<Discount> active) {
        return active.stream().filter(d -> d.appliesTo(product)).mapToInt(Discount::getPercent).max().orElse(0);
    }

    public static BigDecimal discountedPrice(BigDecimal price, int percent) {
        return price.multiply(BigDecimal.valueOf(100L - percent)).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public List<DiscountResponse> list(Discount.Status status) {
        long storeId = CurrentUser.get().storeId();
        Instant now = Instant.now();
        List<Discount> found = switch (status) {
            case ACTIVE -> discounts.findByStoreIdAndStartsAtLessThanEqualAndEndsAtGreaterThanOrderByEndsAtAsc(storeId, now, now);
            case UPCOMING -> discounts.findByStoreIdAndStartsAtGreaterThanOrderByStartsAtAsc(storeId, now);
            case PAST -> discounts.findTop100ByStoreIdAndEndsAtLessThanEqualOrderByEndsAtDesc(storeId, now);
        };
        return withTargetNames(found, now);
    }

    public DiscountResponse create(DiscountRequest req) {
        var me = CurrentUser.get();
        Discount discount = new Discount(me.storeId(), me.userId());
        fill(discount, req);
        return withTargetNames(List.of(discounts.save(discount)), Instant.now()).getFirst();
    }

    /** Seule une remise à venir se modifie : une remise commencée a déjà servi à des ventes. */
    public DiscountResponse update(long id, DiscountRequest req) {
        Discount discount = upcoming(get(id), "modifiée");
        fill(discount, req);
        return withTargetNames(List.of(discount), Instant.now()).getFirst();
    }

    public void delete(long id) {
        discounts.delete(upcoming(get(id), "supprimée"));
    }

    public DiscountResponse end(long id) {
        Discount discount = get(id);
        Instant now = Instant.now();
        if (discount.status(now) != Discount.Status.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "DISCOUNT_NOT_ACTIVE", "Seule une remise en cours peut être terminée");
        }
        discount.end(now);
        return withTargetNames(List.of(discount), now).getFirst();
    }

    private Discount get(long id) {
        return discounts.findByIdAndStoreId(id, CurrentUser.get().storeId())
                .orElseThrow(() -> ApiException.notFound("Remise"));
    }

    private static Discount upcoming(Discount discount, String action) {
        if (discount.status(Instant.now()) != Discount.Status.UPCOMING) {
            throw new ApiException(HttpStatus.CONFLICT, "DISCOUNT_STARTED",
                    "Une remise commencée ne peut plus être " + action + " (terminez-la)");
        }
        return discount;
    }

    private void fill(Discount discount, DiscountRequest req) {
        if (!req.endsAt().isAfter(req.startsAt())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "La fin doit être après le début");
        }
        if (!req.endsAt().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "La remise est déjà terminée");
        }
        // la cible doit exister dans ce store (404 sinon)
        if (req.targetType() == Discount.Target.PRODUCT) products.get(req.targetId());
        else categories.get(req.targetId());
        discount.update(req.label().trim(), req.percent(), req.targetType(), req.targetId(), req.startsAt(), req.endsAt());
    }

    private List<DiscountResponse> withTargetNames(List<Discount> list, Instant now) {
        Map<Long, String> productNames = new HashMap<>();
        Map<Long, String> categoryNames = new HashMap<>();
        productRepo.findAllById(idsOf(list, Discount.Target.PRODUCT)).forEach(p -> productNames.put(p.getId(), p.getName()));
        categoryRepo.findAllById(idsOf(list, Discount.Target.CATEGORY)).forEach(c -> categoryNames.put(c.getId(), c.getName()));
        return list.stream().map(d -> new DiscountResponse(d.getId(), d.getLabel(), d.getPercent(), d.getTargetType(),
                d.getTargetId(),
                (d.getTargetType() == Discount.Target.PRODUCT ? productNames : categoryNames).get(d.getTargetId()),
                d.getStartsAt(), d.getEndsAt(), d.status(now))).toList();
    }

    private static List<Long> idsOf(List<Discount> list, Discount.Target type) {
        return list.stream().filter(d -> d.getTargetType() == type).map(Discount::getTargetId).distinct().toList();
    }
}
