package com.showcase.product.infrastructure.rest.filter;

import static com.showcase.common.domain.filter.FilterOperator.BETWEEN;
import static com.showcase.common.domain.filter.FilterOperator.EQ;
import static com.showcase.common.domain.filter.FilterOperator.GT;
import static com.showcase.common.domain.filter.FilterOperator.GTE;
import static com.showcase.common.domain.filter.FilterOperator.IN;
import static com.showcase.common.domain.filter.FilterOperator.LIKE;
import static com.showcase.common.domain.filter.FilterOperator.LT;
import static com.showcase.common.domain.filter.FilterOperator.LTE;
import static com.showcase.common.domain.filter.FilterOperator.STARTS_WITH;

import com.showcase.common.infrastructure.rest.filter.FilterFields;
import com.showcase.common.infrastructure.rest.filter.FilterableField;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Lista blanca de filtros y campos de ordenación del catálogo.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProductQueryFields {

    private static final FilterableField CREATED_AT = FilterableField.of("createdAt", Instant.class, GTE, LTE, BETWEEN);

    public static final FilterFields PRODUCT_FILTERS = FilterFields.of(
            FilterableField.of("name", String.class, EQ, LIKE, STARTS_WITH),
            FilterableField.of("price", BigDecimal.class, EQ, GT, GTE, LT, LTE, BETWEEN),
            FilterableField.of("stock", Integer.class, EQ, GT, GTE, LT, LTE),
            FilterableField.of("categoryId", Long.class, EQ, IN),
            FilterableField.of("categoryName", String.class, EQ, LIKE),
            CREATED_AT);

    public static final Set<String> PRODUCT_SORT = Set.of("id", "name", "price", "stock", "createdAt", "updatedAt");

    public static final FilterFields CATEGORY_FILTERS = FilterFields.of(
            FilterableField.of("name", String.class, EQ, LIKE, STARTS_WITH),
            CREATED_AT);

    public static final Set<String> CATEGORY_SORT = Set.of("id", "name", "createdAt");
}
