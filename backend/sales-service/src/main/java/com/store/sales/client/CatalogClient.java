package com.store.sales.client;

import com.store.common.contract.StockContract.Reservation;
import com.store.common.contract.StockContract.StockRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

/** Routes internes du catalogue, appelées avec le JWT du caissier et le secret de service. */
@FeignClient(name = "catalog-service")
public interface CatalogClient {

    @PostMapping("/internal/stock/decrement")
    Reservation decrement(@RequestBody StockRequest request);

    @PostMapping("/internal/stock/reservations/{id}/release")
    void release(@PathVariable UUID id);
}
