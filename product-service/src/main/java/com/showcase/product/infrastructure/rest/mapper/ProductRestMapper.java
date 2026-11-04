package com.showcase.product.infrastructure.rest.mapper;

import com.showcase.product.domain.model.Category;
import com.showcase.product.domain.model.Product;
import com.showcase.product.infrastructure.rest.dto.response.CategoryResponseDTO;
import com.showcase.product.infrastructure.rest.dto.response.ProductResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Domain models → response DTOs. Requests are not mapped to models: the controllers pass their
 * fields to the services, which create the models with their rules.
 */
@Mapper(componentModel = "spring")
public interface ProductRestMapper {

    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    ProductResponseDTO toResponse(Product product);

    CategoryResponseDTO toResponse(Category category);
}
