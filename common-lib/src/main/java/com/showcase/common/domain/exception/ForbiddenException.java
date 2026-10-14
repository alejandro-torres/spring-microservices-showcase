package com.showcase.common.domain.exception;

/**
 * El usuario está autenticado pero no tiene permiso para la operación.
 */
public class ForbiddenException extends DomainException {

    public ForbiddenException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public ForbiddenException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
