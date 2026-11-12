package com.showcase.common.domain.exception;

/**
 * A dynamic filter in the URL is invalid: format, field, operator or value.
 */
public class InvalidFilterException extends InvalidRequestException {

    public InvalidFilterException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public InvalidFilterException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
