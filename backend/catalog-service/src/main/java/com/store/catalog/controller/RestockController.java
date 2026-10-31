package com.store.catalog.controller;

import com.store.catalog.dto.Dtos.RestockRequest;
import com.store.catalog.dto.Dtos.RestockResponse;
import com.store.catalog.service.RestockService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/catalog/restocks")
public class RestockController {

    private final RestockService restocks;

    public RestockController(RestockService restocks) {
        this.restocks = restocks;
    }

    /** from / to : jours (fuseau du store), bornes incluses. */
    @GetMapping
    public Page<RestockResponse> history(@RequestParam(required = false) Long productId,
                                         @RequestParam(required = false) Long supplierId,
                                         @RequestParam(required = false) LocalDate from,
                                         @RequestParam(required = false) LocalDate to,
                                         @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable page) {
        return restocks.history(productId, supplierId, from, to, page).map(RestockResponse::of);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('STOCK_RESTOCK')")
    public RestockResponse restock(@Valid @RequestBody RestockRequest req) {
        return RestockResponse.of(restocks.restock(req));
    }
}
