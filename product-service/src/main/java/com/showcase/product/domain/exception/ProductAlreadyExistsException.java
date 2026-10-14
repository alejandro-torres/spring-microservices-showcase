package com.showcase.product.domain.exception;

import com.showcase.common.domain.exception.DuplicateResourceException;

/**
 * Ya existe un producto con ese nombre.
 */
public class ProductAlreadyExistsException extends DuplicateResourceException {

    public ProductAlreadyExistsException(Object... args) {
        super(ProductErrorCode.PRODUCT_ALREADY_EXISTS, args);
    }
}
