package com.showcase.common.domain.exception;

/**
 * A resource with the same unique data already exists (name, email...).
 */
public class DuplicateResourceException extends DomainException {

    public DuplicateResourceException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public DuplicateResourceException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
