package com.store.catalog.service;

import com.store.catalog.dto.Dtos.CategoryResponse;
import com.store.catalog.entity.Category;
import com.store.catalog.repository.CategoryRepository;
import com.store.catalog.repository.ProductRepository;
import com.store.common.security.CurrentUser;
import com.store.common.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class CategoryService {

    private final CategoryRepository categories;
    private final ProductRepository products;

    public CategoryService(CategoryRepository categories, ProductRepository products) {
        this.categories = categories;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        long storeId = CurrentUser.get().storeId();
        Map<Long, Long> counts = products.countActiveByCategory(storeId).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
        return categories.findByStoreIdAndDeletedFalseOrderByNameAsc(storeId).stream()
                .map(c -> new CategoryResponse(c.getId(), c.getName(), counts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Category get(long id) {
        return categories.findByIdAndStoreIdAndDeletedFalse(id, CurrentUser.get().storeId())
                .orElseThrow(() -> ApiException.notFound("Catégorie"));
    }

    public Category create(String name) {
        long storeId = CurrentUser.get().storeId();
        checkNameFree(storeId, name.trim(), 0);
        return categories.save(new Category(storeId, name.trim()));
    }

    public Category rename(long id, String name) {
        Category category = get(id);
        checkNameFree(category.getStoreId(), name.trim(), id);
        category.setName(name.trim());
        return category;
    }

    /** Suppression logique, refusée tant que la catégorie contient des produits actifs. */
    public void delete(long id) {
        Category category = get(id);
        if (products.existsByCategoryIdAndDeletedFalse(id)) {
            throw new ApiException(HttpStatus.CONFLICT, "CATEGORY_NOT_EMPTY",
                    "La catégorie contient encore des produits");
        }
        category.delete();
    }

    private void checkNameFree(long storeId, String name, long excludedId) {
        if (categories.existsByStoreIdAndNameIgnoreCaseAndDeletedFalseAndIdNot(storeId, name, excludedId)) {
            throw new ApiException(HttpStatus.CONFLICT, "NAME_TAKEN", "Une catégorie porte déjà ce nom");
        }
    }
}
