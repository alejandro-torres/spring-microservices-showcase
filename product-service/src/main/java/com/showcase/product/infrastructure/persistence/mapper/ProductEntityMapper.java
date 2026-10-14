package com.showcase.product.infrastructure.persistence.mapper;

import com.showcase.product.domain.model.Product;
import com.showcase.product.infrastructure.persistence.entity.ProductEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", uses = CategoryEntityMapper.class)
public interface ProductEntityMapper {

    Product toDomain(ProductEntity entity);

    /** La categoría la asigna el repositorio con una referencia a la entidad existente. */
    @Mapping(target = "category", ignore = true)
    ProductEntity toEntity(Product product);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "category", ignore = true)
    void updateEntity(Product product, @MappingTarget ProductEntity entity);
}
