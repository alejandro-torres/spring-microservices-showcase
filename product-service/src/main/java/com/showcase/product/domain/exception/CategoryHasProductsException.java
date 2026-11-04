package com.showcase.product.domain.exception;

import com.showcase.common.domain.exception.ConflictException;

/**
 * The category has active products and cannot be deleted.
 */
public class CategoryHasProductsException extends ConflictException {

    public CategoryHasProductsException(Object... args) {
        super(ProductErrorCode.CATEGORY_HAS_PRODUCTS, args);
    }
}
