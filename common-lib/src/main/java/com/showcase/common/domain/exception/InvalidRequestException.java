package com.showcase.common.domain.exception;

/**
 * The request is invalid: incorrect parameters, pagination or filters.
 */
public class InvalidRequestException extends DomainException {

    public InvalidRequestException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public InvalidRequestException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
