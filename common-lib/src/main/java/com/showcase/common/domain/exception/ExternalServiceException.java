package com.showcase.common.domain.exception;

/**
 * Ha fallado otro microservicio o un sistema externo del que depende la operación.
 */
public class ExternalServiceException extends DomainException {

    public ExternalServiceException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public ExternalServiceException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
