package com.showcase.product.domain.exception;

import com.showcase.common.domain.exception.BusinessRuleException;

/**
 * Not enough stock for the operation.
 */
public class InsufficientStockException extends BusinessRuleException {

    public InsufficientStockException(Object... args) {
        super(ProductErrorCode.INSUFFICIENT_STOCK, args);
    }
}
