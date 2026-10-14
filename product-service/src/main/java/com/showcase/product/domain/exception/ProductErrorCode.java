package com.showcase.product.domain.exception;

import com.showcase.common.domain.exception.ErrorCategory;
import com.showcase.common.domain.exception.ErrorCode;

/**
 * Códigos de error propios de product-service.
 */
public enum ProductErrorCode implements ErrorCode {

    PRODUCT_NOT_FOUND(ErrorCategory.NOT_FOUND),
    CATEGORY_NOT_FOUND(ErrorCategory.NOT_FOUND),
    PRODUCT_ALREADY_EXISTS(ErrorCategory.CONFLICT),
    CATEGORY_ALREADY_EXISTS(ErrorCategory.CONFLICT),
    CATEGORY_HAS_PRODUCTS(ErrorCategory.CONFLICT),
    INSUFFICIENT_STOCK(ErrorCategory.BUSINESS_RULE),
    INVALID_PRODUCT_NAME(ErrorCategory.INVALID_REQUEST),
    INVALID_CATEGORY_NAME(ErrorCategory.INVALID_REQUEST),
    INVALID_PRICE(ErrorCategory.INVALID_REQUEST),
    INVALID_QUANTITY(ErrorCategory.INVALID_REQUEST);

    private final ErrorCategory category;

    ProductErrorCode(ErrorCategory category) {
        this.category = category;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public ErrorCategory category() {
        return category;
    }
}
