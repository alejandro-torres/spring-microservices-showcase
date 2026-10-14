package com.showcase.common.domain.exception;

/**
 * La operación choca con el estado actual del recurso: modificación concurrente
 * (versión distinta) o dependencias activas que impiden borrarlo.
 */
public class ConflictException extends DomainException {

    public ConflictException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public ConflictException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
