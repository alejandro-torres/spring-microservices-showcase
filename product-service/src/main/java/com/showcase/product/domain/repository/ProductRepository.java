package com.showcase.product.domain.repository;

import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.product.domain.model.Product;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida para persistir productos.
 */
public interface ProductRepository {

    Product save(Product product);

    /** Solo productos activos, con su categoría. */
    Optional<Product> findById(Long id);

    PageResult<Product> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive);

    /** Productos activos de una categoría (consulta JPQL con su propia consulta de recuento). */
    PageResult<Product> findActiveByCategory(Long categoryId, PageQuery pageQuery);

    /** Sin distinguir mayúsculas; incluye productos borrados, porque el nombre sigue reservado. */
    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    boolean existsActiveByCategoryId(Long categoryId);
}
