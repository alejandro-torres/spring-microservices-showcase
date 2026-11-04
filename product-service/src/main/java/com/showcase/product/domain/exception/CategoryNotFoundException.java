package com.showcase.product.domain.exception;

import com.showcase.common.domain.exception.ResourceNotFoundException;

/**
 * There is no active category with the given id.
 */
public class CategoryNotFoundException extends ResourceNotFoundException {

    public CategoryNotFoundException(Object... args) {
        super(ProductErrorCode.CATEGORY_NOT_FOUND, args);
    }
}
