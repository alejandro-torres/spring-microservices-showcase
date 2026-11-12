package com.showcase.common.domain.exception;

/**
 * Invalid credentials or expired session.
 */
public class UnauthorizedException extends DomainException {

    public UnauthorizedException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public UnauthorizedException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
