package com.showcase.product.domain.exception;

import com.showcase.common.domain.exception.BusinessRuleException;

/**
 * No hay stock suficiente para la operación.
 */
public class InsufficientStockException extends BusinessRuleException {

    public InsufficientStockException(Object... args) {
        super(ProductErrorCode.INSUFFICIENT_STOCK, args);
    }
}
