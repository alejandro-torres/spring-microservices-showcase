package com.showcase.product.infrastructure.persistence.mapper;

import com.showcase.product.domain.model.Category;
import com.showcase.product.infrastructure.persistence.entity.CategoryEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CategoryEntityMapper {

    Category toDomain(CategoryEntity entity);

    CategoryEntity toEntity(Category category);

    @Mapping(target = "id", ignore = true)
    void updateEntity(Category category, @MappingTarget CategoryEntity entity);
}
