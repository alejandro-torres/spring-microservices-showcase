package com.showcase.product.infrastructure.persistence.specification;

import com.showcase.common.infrastructure.persistence.specification.FilterMapping;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Traducción de los nombres públicos de filtros y ordenación a los paths JPA.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProductFilterMappings {

    public static final FilterMapping PRODUCTS = FilterMapping.of(Map.of(
            "categoryId", "category.id",
            "categoryName", "category.name"));

    public static final FilterMapping CATEGORIES = FilterMapping.identity();
}
