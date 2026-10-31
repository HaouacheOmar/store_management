package com.store.sales.controller;

import com.store.sales.dto.Dtos.SaleRequest;
import com.store.sales.dto.Dtos.SaleResponse;
import com.store.sales.dto.Dtos.SaleSummary;
import com.store.sales.service.SaleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/sales/sales")
public class SaleController {

    private final SaleService sales;

    public SaleController(SaleService sales) {
        this.sales = sales;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SALE_CREATE')")
    public SaleResponse create(@Valid @RequestBody SaleRequest req) {
        return SaleResponse.of(sales.create(req));
    }

    /** date : jour du store (défaut aujourd'hui) ; cashierId : ignoré sans SALES_VIEW_ALL. */
    @GetMapping
    public List<SaleSummary> list(@RequestParam(required = false) LocalDate date,
                                  @RequestParam(required = false) Long cashierId) {
        return sales.list(date, cashierId).stream().map(SaleSummary::of).toList();
    }

    @GetMapping("/{id}")
    public SaleResponse get(@PathVariable long id) {
        return SaleResponse.of(sales.get(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('SALE_CANCEL')")
    public SaleResponse cancel(@PathVariable long id) {
        return SaleResponse.of(sales.cancel(id));
    }
}
