package com.showcase.product.domain.repository;

import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.product.domain.model.Category;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida para persistir categorías.
 */
public interface CategoryRepository {

    Category save(Category category);

    /** Solo categorías activas. */
    Optional<Category> findById(Long id);

    PageResult<Category> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive);

    /** Sin distinguir mayúsculas. */
    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);
}
