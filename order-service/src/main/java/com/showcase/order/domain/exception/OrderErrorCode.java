package com.showcase.order.domain.exception;

import com.showcase.common.domain.exception.ErrorCategory;
import com.showcase.common.domain.exception.ErrorCode;

public enum OrderErrorCode implements ErrorCode {

    ORDER_NOT_FOUND(ErrorCategory.NOT_FOUND),
    CUSTOMER_NOT_FOUND(ErrorCategory.NOT_FOUND),
    PRODUCT_NOT_FOUND(ErrorCategory.NOT_FOUND),
    INVALID_ORDER_DATA(ErrorCategory.INVALID_REQUEST),
    DUPLICATE_ORDER_LINE(ErrorCategory.INVALID_REQUEST),
    CUSTOMER_WITHOUT_ADDRESS(ErrorCategory.BUSINESS_RULE),
    INSUFFICIENT_STOCK(ErrorCategory.BUSINESS_RULE),
    INVALID_STATUS_TRANSITION(ErrorCategory.BUSINESS_RULE),
    ORDER_NOT_MODIFIABLE(ErrorCategory.BUSINESS_RULE),
    ORDER_NOT_DELETABLE(ErrorCategory.BUSINESS_RULE);

    private final ErrorCategory category;

    OrderErrorCode(ErrorCategory category) {
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
