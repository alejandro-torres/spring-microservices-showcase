package com.showcase.common.domain.exception;

/**
 * Un filtro dinámico de la URL no es válido: formato, campo, operador o valor.
 */
public class InvalidFilterException extends InvalidRequestException {

    public InvalidFilterException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public InvalidFilterException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
