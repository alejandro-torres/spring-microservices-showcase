package com.showcase.product.application.service;

import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.product.domain.model.Product;
import java.math.BigDecimal;
import java.util.List;

/**
 * Use cases for the product catalog (inbound port).
 */
public interface ProductService {

    Product create(String name, String description, BigDecimal price, int initialStock, Long categoryId);

    Product findById(Long id);

    PageResult<Product> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive);

    /** Active products of a category; fails with 404 if the category does not exist. */
    PageResult<Product> findByCategory(Long categoryId, PageQuery pageQuery);

    /** {@code version} is the one the client read (optimistic locking). The stock is not changed here. */
    Product update(Long id, String name, String description, BigDecimal price, Long categoryId, Long version);

    /** Soft delete. */
    void delete(Long id);

    /** Manual inventory adjustment: positive for incoming stock, negative for outgoing stock. */
    Product adjustStock(Long id, int delta);
}
