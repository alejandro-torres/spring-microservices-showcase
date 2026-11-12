package com.showcase.common.domain.exception;

/**
 * The requested resource does not exist or has been deleted (soft delete).
 */
public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public ResourceNotFoundException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
