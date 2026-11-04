package com.showcase.product.domain.exception;

import com.showcase.common.domain.exception.ResourceNotFoundException;

/**
 * There is no active product with the given id.
 */
public class ProductNotFoundException extends ResourceNotFoundException {

    public ProductNotFoundException(Object... args) {
        super(ProductErrorCode.PRODUCT_NOT_FOUND, args);
    }
}
