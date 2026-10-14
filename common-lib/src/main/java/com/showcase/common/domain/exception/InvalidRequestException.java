package com.showcase.common.domain.exception;

/**
 * La petición no es válida: parámetros, paginación o filtros incorrectos.
 */
public class InvalidRequestException extends DomainException {

    public InvalidRequestException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public InvalidRequestException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
