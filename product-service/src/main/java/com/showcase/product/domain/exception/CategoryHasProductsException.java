package com.showcase.product.domain.exception;

import com.showcase.common.domain.exception.ConflictException;

/**
 * La categoría tiene productos activos y no se puede borrar.
 */
public class CategoryHasProductsException extends ConflictException {

    public CategoryHasProductsException(Object... args) {
        super(ProductErrorCode.CATEGORY_HAS_PRODUCTS, args);
    }
}
