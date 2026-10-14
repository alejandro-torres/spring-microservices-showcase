package com.showcase.product.domain.exception;

import com.showcase.common.domain.exception.ResourceNotFoundException;

/**
 * No existe un producto activo con el id indicado.
 */
public class ProductNotFoundException extends ResourceNotFoundException {

    public ProductNotFoundException(Object... args) {
        super(ProductErrorCode.PRODUCT_NOT_FOUND, args);
    }
}
