package com.showcase.product.infrastructure.persistence.specification;

import com.showcase.common.infrastructure.persistence.specification.FilterMapping;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Translation of the public filter and sort names to JPA paths.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProductFilterMappings {

    public static final FilterMapping PRODUCTS = FilterMapping.of(Map.of(
            "categoryId", "category.id",
            "categoryName", "category.name"));

    public static final FilterMapping CATEGORIES = FilterMapping.identity();
}
