package com.showcase.product.application.service;

import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.product.domain.model.Category;
import java.util.List;

/**
 * Casos de uso de las categorías (puerto de entrada).
 */
public interface CategoryService {

    Category create(String name, String description);

    Category findById(Long id);

    PageResult<Category> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive);

    Category update(Long id, String name, String description, Long version);

    /** Borrado lógico; falla con 409 si la categoría tiene productos activos. */
    void delete(Long id);
}
