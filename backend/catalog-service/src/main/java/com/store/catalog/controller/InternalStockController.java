package com.store.catalog.controller;

import com.store.catalog.service.StockService;
import com.store.common.contract.StockContract.Reservation;
import com.store.common.contract.StockContract.StockRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Appelé par sales-service uniquement : /internal/** n'est pas routé par la gateway et exige en plus
 * le secret de service (InternalApiFilter) ; le JWT transmis est celui du caissier.
 */
@RestController
@RequestMapping("/internal/stock")
@PreAuthorize("hasAuthority('SALE_CREATE')")
public class InternalStockController {

    private final StockService stock;

    public InternalStockController(StockService stock) {
        this.stock = stock;
    }

    @PostMapping("/decrement")
    public Reservation decrement(@RequestBody StockRequest req) {
        return stock.decrement(req.lines());
    }

    /** Compensation : la vente n'a pas pu être enregistrée après la sortie de stock. */
    @PostMapping("/reservations/{id}/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@PathVariable UUID id) {
        stock.release(id);
    }
}
