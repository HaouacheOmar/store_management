package com.store.sales.client;

import com.store.common.contract.StockContract.PricedLine;
import com.store.common.contract.StockContract.StockRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/** Routes internes du catalogue, appelées avec le JWT du caissier. */
@FeignClient(name = "catalog-service")
public interface CatalogClient {

    @PostMapping("/internal/stock/decrement")
    List<PricedLine> decrement(@RequestBody StockRequest request);

    @PostMapping("/internal/stock/increment")
    void increment(@RequestBody StockRequest request);
}
