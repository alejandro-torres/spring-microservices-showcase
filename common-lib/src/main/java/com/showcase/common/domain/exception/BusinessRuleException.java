package com.showcase.common.domain.exception;

/**
 * La operación incumple una regla de negocio (p. ej. stock insuficiente).
 */
public class BusinessRuleException extends DomainException {

    public BusinessRuleException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public BusinessRuleException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
