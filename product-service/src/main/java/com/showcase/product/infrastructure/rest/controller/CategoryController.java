package com.showcase.product.infrastructure.rest.controller;

import static com.showcase.product.infrastructure.rest.filter.ProductQueryFields.CATEGORY_FILTERS;
import static com.showcase.product.infrastructure.rest.filter.ProductQueryFields.CATEGORY_SORT;
import static com.showcase.product.infrastructure.rest.filter.ProductQueryFields.PRODUCT_SORT;

import com.showcase.common.infrastructure.rest.dto.response.PageResponseDTO;
import com.showcase.common.infrastructure.rest.filter.FilterCriteriaParser;
import com.showcase.common.infrastructure.rest.filter.FilterParameters;
import com.showcase.common.infrastructure.rest.page.PageQueryParser;
import com.showcase.product.application.service.CategoryService;
import com.showcase.product.application.service.ProductService;
import com.showcase.product.domain.model.Category;
import com.showcase.product.infrastructure.rest.dto.request.CreateCategoryRequestDTO;
import com.showcase.product.infrastructure.rest.dto.request.UpdateCategoryRequestDTO;
import com.showcase.product.infrastructure.rest.dto.response.CategoryResponseDTO;
import com.showcase.product.infrastructure.rest.dto.response.ProductResponseDTO;
import com.showcase.product.infrastructure.rest.mapper.ProductRestMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * API de categorías. Usa los mismos permisos que el catálogo (product:read / product:write).
 */
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;
    private final ProductService productService;
    private final ProductRestMapper mapper;
    private final FilterCriteriaParser filterParser;

    public CategoryController(CategoryService categoryService, ProductService productService,
                              ProductRestMapper mapper, FilterCriteriaParser filterParser) {
        this.categoryService = categoryService;
        this.productService = productService;
        this.mapper = mapper;
        this.filterParser = filterParser;
    }

    @PreAuthorize("hasAuthority('product:write')")
    @PostMapping
    public ResponseEntity<CategoryResponseDTO> create(@Valid @RequestBody CreateCategoryRequestDTO request) {
        Category category = categoryService.create(request.name(), request.description());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(category.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(category));
    }

    @PreAuthorize("hasAuthority('product:read')")
    @GetMapping("/{id}")
    public CategoryResponseDTO findById(@PathVariable @Positive Long id) {
        return mapper.toResponse(categoryService.findById(id));
    }

    @PreAuthorize("hasAuthority('product:read') and (!#includeInactive or hasRole('ADMIN'))")
    @GetMapping
    public PageResponseDTO<CategoryResponseDTO> findAll(
            FilterParameters filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sort,
            @RequestParam(defaultValue = "ASC") String direction,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        var result = categoryService.findAll(
                filterParser.parse(filter.values(), CATEGORY_FILTERS),
                PageQueryParser.parse(page, size, sort, direction, CATEGORY_SORT),
                includeInactive);
        return PageResponseDTO.from(result, mapper::toResponse);
    }

    /** Productos activos de la categoría (consulta JPQL paginada). */
    @PreAuthorize("hasAuthority('product:read')")
    @GetMapping("/{id}/products")
    public PageResponseDTO<ProductResponseDTO> findProducts(
            @PathVariable @Positive Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "ASC") String direction) {
        var result = productService.findByCategory(id, PageQueryParser.parse(page, size, sort, direction, PRODUCT_SORT));
        return PageResponseDTO.from(result, mapper::toResponse);
    }

    @PreAuthorize("hasAuthority('product:write')")
    @PutMapping("/{id}")
    public CategoryResponseDTO update(@PathVariable @Positive Long id,
                                      @Valid @RequestBody UpdateCategoryRequestDTO request) {
        return mapper.toResponse(categoryService.update(id, request.name(), request.description(), request.version()));
    }

    @PreAuthorize("hasAuthority('product:write')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
