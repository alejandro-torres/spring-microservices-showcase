package com.showcase.product.domain.exception;

import com.showcase.common.domain.exception.DuplicateResourceException;

/**
 * A category with that name already exists.
 */
public class CategoryAlreadyExistsException extends DuplicateResourceException {

    public CategoryAlreadyExistsException(Object... args) {
        super(ProductErrorCode.CATEGORY_ALREADY_EXISTS, args);
    }
}
