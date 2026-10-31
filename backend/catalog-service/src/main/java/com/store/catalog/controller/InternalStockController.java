package com.store.catalog.controller;

import com.store.catalog.service.StockService;
import com.store.common.contract.StockContract.PricedLine;
import com.store.common.contract.StockContract.StockRequest;
import com.store.common.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Appelé par sales-service uniquement : /internal/** n'est pas routé par la gateway. */
@RestController
@RequestMapping("/internal/stock")
@PreAuthorize("hasAuthority('SALE_CREATE')")
public class InternalStockController {

    private final StockService stock;

    public InternalStockController(StockService stock) {
        this.stock = stock;
    }

    @PostMapping("/decrement")
    public List<PricedLine> decrement(@RequestBody StockRequest req) {
        return stock.decrement(req.lines());
    }

    /** Compensation : la vente n'a pas pu être enregistrée après la sortie de stock. */
    @PostMapping("/increment")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void increment(@RequestBody StockRequest req) {
        stock.increment(CurrentUser.get().storeId(), req.lines());
    }
}
