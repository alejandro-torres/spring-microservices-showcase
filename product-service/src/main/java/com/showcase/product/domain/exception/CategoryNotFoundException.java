package com.showcase.product.domain.exception;

import com.showcase.common.domain.exception.ResourceNotFoundException;

/**
 * No existe una categoría activa con el id indicado.
 */
public class CategoryNotFoundException extends ResourceNotFoundException {

    public CategoryNotFoundException(Object... args) {
        super(ProductErrorCode.CATEGORY_NOT_FOUND, args);
    }
}
