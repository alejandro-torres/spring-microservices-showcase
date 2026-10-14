package com.showcase.product.infrastructure.rest.mapper;

import com.showcase.product.domain.model.Category;
import com.showcase.product.domain.model.Product;
import com.showcase.product.infrastructure.rest.dto.response.CategoryResponseDTO;
import com.showcase.product.infrastructure.rest.dto.response.ProductResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Modelos de dominio → DTOs de respuesta. Las peticiones no se mapean a modelos: los controllers pasan
 * sus campos a los servicios, que crean los modelos con sus reglas.
 */
@Mapper(componentModel = "spring")
public interface ProductRestMapper {

    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    ProductResponseDTO toResponse(Product product);

    CategoryResponseDTO toResponse(Category category);
}
