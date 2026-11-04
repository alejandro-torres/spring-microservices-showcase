package com.showcase.common.domain.exception;

/**
 * Another microservice or an external system the operation depends on has failed.
 */
public class ExternalServiceException extends DomainException {

    public ExternalServiceException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public ExternalServiceException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
