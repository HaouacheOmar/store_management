package com.store.catalog.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Appelle supplier-service avec le JWT de l'utilisateur : un fournisseur d'un autre store donne 404. */
@FeignClient(name = "supplier-service")
public interface SupplierClient {

    record SupplierRef(long id, String name) {
    }

    @GetMapping("/suppliers/{id}")
    SupplierRef get(@PathVariable long id);
}
