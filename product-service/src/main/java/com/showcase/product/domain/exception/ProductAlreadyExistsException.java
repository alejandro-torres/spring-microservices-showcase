package com.showcase.product.domain.exception;

import com.showcase.common.domain.exception.DuplicateResourceException;

/**
 * A product with that name already exists.
 */
public class ProductAlreadyExistsException extends DuplicateResourceException {

    public ProductAlreadyExistsException(Object... args) {
        super(ProductErrorCode.PRODUCT_ALREADY_EXISTS, args);
    }
}
