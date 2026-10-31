package com.store.catalog.controller;

import com.store.catalog.dto.Dtos.ProductRequest;
import com.store.catalog.dto.Dtos.ProductResponse;
import com.store.catalog.dto.Dtos.StockFilter;
import com.store.catalog.entity.Discount;
import com.store.catalog.entity.Product;
import com.store.catalog.service.DiscountService;
import com.store.catalog.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/catalog/products")
public class ProductController {

    private final ProductService products;
    private final DiscountService discounts;

    public ProductController(ProductService products, DiscountService discounts) {
        this.products = products;
        this.discounts = discounts;
    }

    @GetMapping
    public Page<ProductResponse> search(@RequestParam(required = false) Long categoryId,
                                        @RequestParam(required = false) String q,
                                        @RequestParam(defaultValue = "ALL") StockFilter stock,
                                        @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable page) {
        List<Discount> active = discounts.active();
        return products.search(categoryId, q, stock, page).map(p -> view(p, active));
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable long id) {
        return view(products.get(id), discounts.active());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public ProductResponse create(@Valid @RequestBody ProductRequest req) {
        return view(products.create(req), discounts.active());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public ProductResponse update(@PathVariable long id, @Valid @RequestBody ProductRequest req) {
        return view(products.update(id, req), discounts.active());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public void delete(@PathVariable long id) {
        products.delete(id);
    }

    private static ProductResponse view(Product p, List<Discount> active) {
        int percent = DiscountService.percentFor(p, active);
        return ProductResponse.of(p, percent, DiscountService.discountedPrice(p.getPrice(), percent));
    }
}
