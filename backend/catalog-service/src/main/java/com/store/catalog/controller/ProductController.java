package com.store.catalog.controller;

import com.store.catalog.dto.Dtos.ProductRequest;
import com.store.catalog.dto.Dtos.ProductResponse;
import com.store.catalog.dto.Dtos.StockFilter;
import com.store.catalog.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/catalog/products")
public class ProductController {

    private final ProductService products;

    public ProductController(ProductService products) {
        this.products = products;
    }

    @GetMapping
    public Page<ProductResponse> search(@RequestParam(required = false) Long categoryId,
                                        @RequestParam(required = false) String q,
                                        @RequestParam(defaultValue = "ALL") StockFilter stock,
                                        @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable page) {
        return products.search(categoryId, q, stock, page).map(ProductResponse::of);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable long id) {
        return ProductResponse.of(products.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public ProductResponse create(@Valid @RequestBody ProductRequest req) {
        return ProductResponse.of(products.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public ProductResponse update(@PathVariable long id, @Valid @RequestBody ProductRequest req) {
        return ProductResponse.of(products.update(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public void delete(@PathVariable long id) {
        products.delete(id);
    }
}
