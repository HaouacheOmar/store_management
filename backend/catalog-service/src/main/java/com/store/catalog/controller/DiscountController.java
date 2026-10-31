package com.store.catalog.controller;

import com.store.catalog.dto.Dtos.DiscountRequest;
import com.store.catalog.dto.Dtos.DiscountResponse;
import com.store.catalog.entity.Discount;
import com.store.catalog.service.DiscountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/catalog/discounts")
public class DiscountController {

    private final DiscountService discounts;

    public DiscountController(DiscountService discounts) {
        this.discounts = discounts;
    }

    @GetMapping
    public List<DiscountResponse> list(@RequestParam(defaultValue = "ACTIVE") Discount.Status status) {
        return discounts.list(status);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('DISCOUNT_WRITE')")
    public DiscountResponse create(@Valid @RequestBody DiscountRequest req) {
        return discounts.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('DISCOUNT_WRITE')")
    public DiscountResponse update(@PathVariable long id, @Valid @RequestBody DiscountRequest req) {
        return discounts.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('DISCOUNT_WRITE')")
    public void delete(@PathVariable long id) {
        discounts.delete(id);
    }

    @PostMapping("/{id}/end")
    @PreAuthorize("hasAuthority('DISCOUNT_WRITE')")
    public DiscountResponse end(@PathVariable long id) {
        return discounts.end(id);
    }
}
