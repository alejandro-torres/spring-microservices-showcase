package com.showcase.product.infrastructure.rest.controller;

import static com.showcase.product.infrastructure.rest.filter.ProductQueryFields.PRODUCT_FILTERS;
import static com.showcase.product.infrastructure.rest.filter.ProductQueryFields.PRODUCT_SORT;

import com.showcase.common.infrastructure.rest.dto.response.PageResponseDTO;
import com.showcase.common.infrastructure.rest.filter.FilterCriteriaParser;
import com.showcase.common.infrastructure.rest.filter.FilterParameters;
import com.showcase.common.infrastructure.rest.page.PageQueryParser;
import com.showcase.product.application.service.ProductService;
import com.showcase.product.domain.model.Product;
import com.showcase.product.infrastructure.rest.dto.request.AdjustStockRequestDTO;
import com.showcase.product.infrastructure.rest.dto.request.CreateProductRequestDTO;
import com.showcase.product.infrastructure.rest.dto.request.UpdateProductRequestDTO;
import com.showcase.product.infrastructure.rest.dto.response.ProductResponseDTO;
import com.showcase.product.infrastructure.rest.mapper.ProductRestMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import lombok.RequiredArgsConstructor;
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
 * API del catálogo. Solo conoce la interfaz {@link ProductService}.
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductRestMapper mapper;
    private final FilterCriteriaParser filterParser;

    @PreAuthorize("hasAuthority('product:write')")
    @PostMapping
    public ResponseEntity<ProductResponseDTO> create(@Valid @RequestBody CreateProductRequestDTO request) {
        Product product = productService.create(request.name(), request.description(), request.price(),
                request.stock(), request.categoryId());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(product.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(product));
    }

    @PreAuthorize("hasAuthority('product:read')")
    @GetMapping("/{id}")
    public ProductResponseDTO findById(@PathVariable @Positive Long id) {
        return mapper.toResponse(productService.findById(id));
    }

    /**
     * P. ej. {@code ?filter=name:like:portatil&filter=price:between:100,500&filter=categoryId:in:1,2&sort=price&direction=DESC}.
     */
    @PreAuthorize("hasAuthority('product:read') and (!#includeInactive or hasRole('ADMIN'))")
    @GetMapping
    public PageResponseDTO<ProductResponseDTO> findAll(
            FilterParameters filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sort,
            @RequestParam(defaultValue = "ASC") String direction,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        var result = productService.findAll(
                filterParser.parse(filter.values(), PRODUCT_FILTERS),
                PageQueryParser.parse(page, size, sort, direction, PRODUCT_SORT),
                includeInactive);
        return PageResponseDTO.from(result, mapper::toResponse);
    }

    @PreAuthorize("hasAuthority('product:write')")
    @PutMapping("/{id}")
    public ProductResponseDTO update(@PathVariable @Positive Long id,
                                     @Valid @RequestBody UpdateProductRequestDTO request) {
        return mapper.toResponse(productService.update(id, request.name(), request.description(), request.price(),
                request.categoryId(), request.version()));
    }

    @PreAuthorize("hasAuthority('product:write')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Ajuste manual de inventario: solo ADMIN. */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/stock")
    public ProductResponseDTO adjustStock(@PathVariable @Positive Long id,
                                          @Valid @RequestBody AdjustStockRequestDTO request) {
        return mapper.toResponse(productService.adjustStock(id, request.delta()));
    }
}
