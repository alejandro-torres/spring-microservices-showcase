package com.showcase.common.domain.exception;

/**
 * Ya existe un recurso con los mismos datos únicos (nombre, email...).
 */
public class DuplicateResourceException extends DomainException {

    public DuplicateResourceException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public DuplicateResourceException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
