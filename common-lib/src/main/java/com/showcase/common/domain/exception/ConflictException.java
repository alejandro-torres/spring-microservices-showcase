package com.showcase.common.domain.exception;

/**
 * The operation clashes with the current state of the resource: concurrent modification
 * (different version) or active dependencies that prevent deleting it.
 */
public class ConflictException extends DomainException {

    public ConflictException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public ConflictException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
