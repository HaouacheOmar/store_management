package com.store.catalog.controller;

import com.store.catalog.dto.Dtos.CategoryRequest;
import com.store.catalog.dto.Dtos.CategoryResponse;
import com.store.catalog.entity.Category;
import com.store.catalog.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/catalog/categories")
public class CategoryController {

    private final CategoryService categories;

    public CategoryController(CategoryService categories) {
        this.categories = categories;
    }

    @GetMapping
    public List<CategoryResponse> list() {
        return categories.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CATEGORY_WRITE')")
    public CategoryResponse create(@Valid @RequestBody CategoryRequest req) {
        Category c = categories.create(req.name());
        return new CategoryResponse(c.getId(), c.getName(), 0);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CATEGORY_WRITE')")
    public void rename(@PathVariable long id, @Valid @RequestBody CategoryRequest req) {
        categories.rename(id, req.name());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('CATEGORY_WRITE')")
    public void delete(@PathVariable long id) {
        categories.delete(id);
    }
}
