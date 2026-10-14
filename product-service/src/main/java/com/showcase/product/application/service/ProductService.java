package com.showcase.product.application.service;

import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.product.domain.model.Product;
import java.math.BigDecimal;
import java.util.List;

/**
 * Casos de uso del catálogo de productos (puerto de entrada).
 */
public interface ProductService {

    Product create(String name, String description, BigDecimal price, int initialStock, Long categoryId);

    Product findById(Long id);

    PageResult<Product> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive);

    /** Productos activos de una categoría; falla con 404 si la categoría no existe. */
    PageResult<Product> findByCategory(Long categoryId, PageQuery pageQuery);

    /** {@code version} es la que leyó el cliente (bloqueo optimista). El stock no se cambia aquí. */
    Product update(Long id, String name, String description, BigDecimal price, Long categoryId, Long version);

    /** Borrado lógico. */
    void delete(Long id);

    /** Ajuste manual de inventario: positivo para entradas, negativo para salidas. */
    Product adjustStock(Long id, int delta);
}
