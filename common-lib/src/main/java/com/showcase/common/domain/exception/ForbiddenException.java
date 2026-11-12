package com.showcase.common.domain.exception;

/**
 * The user is authenticated but lacks permission for the operation.
 */
public class ForbiddenException extends DomainException {

    public ForbiddenException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public ForbiddenException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
