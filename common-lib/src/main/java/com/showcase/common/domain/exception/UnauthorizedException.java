package com.showcase.common.domain.exception;

/**
 * Credenciales inválidas o sesión caducada.
 */
public class UnauthorizedException extends DomainException {

    public UnauthorizedException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public UnauthorizedException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
