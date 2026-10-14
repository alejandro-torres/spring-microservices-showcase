package com.showcase.customer.domain.exception;

import com.showcase.common.domain.exception.ErrorCategory;
import com.showcase.common.domain.exception.ErrorCode;

public enum CustomerErrorCode implements ErrorCode {

    CUSTOMER_NOT_FOUND(ErrorCategory.NOT_FOUND),
    ADDRESS_NOT_FOUND(ErrorCategory.NOT_FOUND),
    EMAIL_ALREADY_EXISTS(ErrorCategory.CONFLICT),
    USER_ALREADY_HAS_CUSTOMER(ErrorCategory.CONFLICT),
    INVALID_CUSTOMER_DATA(ErrorCategory.INVALID_REQUEST),
    TOO_MANY_ADDRESSES(ErrorCategory.BUSINESS_RULE);

    private final ErrorCategory category;

    CustomerErrorCode(ErrorCategory category) {
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
