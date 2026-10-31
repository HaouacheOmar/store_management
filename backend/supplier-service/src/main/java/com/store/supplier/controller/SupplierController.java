package com.store.supplier.controller;

import com.store.common.security.CurrentUser;
import com.store.common.web.ApiException;
import com.store.supplier.entity.Supplier;
import com.store.supplier.repository.SupplierRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** CRUD simple : pas de couche service tant qu'il n'y a pas de logique métier au-delà de l'unicité du nom. */
@RestController
@RequestMapping("/suppliers")
@Transactional
public class SupplierController {

    public record SupplierRequest(@NotBlank @Size(max = 120) String name,
                                  @Size(max = 30) String phone,
                                  @Email @Size(max = 180) String email,
                                  @Size(max = 255) String address) {
    }

    public record SupplierResponse(long id, String name, String phone, String email, String address) {
        static SupplierResponse of(Supplier s) {
            return new SupplierResponse(s.getId(), s.getName(), s.getPhone(), s.getEmail(), s.getAddress());
        }
    }

    private final SupplierRepository suppliers;

    public SupplierController(SupplierRepository suppliers) {
        this.suppliers = suppliers;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<SupplierResponse> list() {
        return suppliers.findByStoreIdAndDeletedFalseOrderByNameAsc(CurrentUser.get().storeId()).stream()
                .map(SupplierResponse::of).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public SupplierResponse get(@PathVariable long id) {
        return SupplierResponse.of(find(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SUPPLIER_WRITE')")
    public SupplierResponse create(@Valid @RequestBody SupplierRequest req) {
        return SupplierResponse.of(suppliers.save(apply(new Supplier(CurrentUser.get().storeId()), req, 0)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SUPPLIER_WRITE')")
    public SupplierResponse update(@PathVariable long id, @Valid @RequestBody SupplierRequest req) {
        return SupplierResponse.of(apply(find(id), req, id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SUPPLIER_WRITE')")
    public void delete(@PathVariable long id) {
        find(id).delete();
    }

    private Supplier find(long id) {
        return suppliers.findByIdAndStoreIdAndDeletedFalse(id, CurrentUser.get().storeId())
                .orElseThrow(() -> ApiException.notFound("Fournisseur"));
    }

    private Supplier apply(Supplier supplier, SupplierRequest req, long excludedId) {
        String name = req.name().trim();
        if (suppliers.existsByStoreIdAndNameIgnoreCaseAndDeletedFalseAndIdNot(supplier.getStoreId(), name, excludedId)) {
            throw new ApiException(HttpStatus.CONFLICT, "NAME_TAKEN", "Un fournisseur porte déjà ce nom");
        }
        supplier.update(name, blankToNull(req.phone()), blankToNull(req.email()), blankToNull(req.address()));
        return supplier;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
