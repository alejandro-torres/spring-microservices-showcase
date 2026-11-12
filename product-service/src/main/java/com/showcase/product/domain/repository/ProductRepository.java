package com.showcase.product.domain.repository;

import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.product.domain.model.Product;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port to persist products.
 */
public interface ProductRepository {

    Product save(Product product);

    /** Active products only, with their category. */
    Optional<Product> findById(Long id);

    /** Includes deleted ones: when an order is cancelled its stock returns even if the product is no longer sold. */
    Optional<Product> findByIdIncludingInactive(Long id);

    PageResult<Product> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive);

    /** Active products of a category (JPQL query with its own count query). */
    PageResult<Product> findActiveByCategory(Long categoryId, PageQuery pageQuery);

    /** Case-insensitive; includes deleted products, because the name remains reserved. */
    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    boolean existsActiveByCategoryId(Long categoryId);
}
