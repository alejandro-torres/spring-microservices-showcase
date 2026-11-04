package com.showcase.common.domain.exception;

/**
 * The operation breaks a business rule (e.g. insufficient stock).
 */
public class BusinessRuleException extends DomainException {

    public BusinessRuleException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public BusinessRuleException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
