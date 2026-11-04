package com.showcase.product.application.service;

import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.product.domain.model.Category;
import java.util.List;

/**
 * Use cases for categories (inbound port).
 */
public interface CategoryService {

    Category create(String name, String description);

    Category findById(Long id);

    PageResult<Category> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive);

    Category update(Long id, String name, String description, Long version);

    /** Soft delete; fails with 409 if the category has active products. */
    void delete(Long id);
}
