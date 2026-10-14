package com.showcase.product.domain.exception;

import com.showcase.common.domain.exception.DuplicateResourceException;

/**
 * Ya existe una categoría con ese nombre.
 */
public class CategoryAlreadyExistsException extends DuplicateResourceException {

    public CategoryAlreadyExistsException(Object... args) {
        super(ProductErrorCode.CATEGORY_ALREADY_EXISTS, args);
    }
}
