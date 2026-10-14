package com.showcase.common.domain.exception;

/**
 * El recurso pedido no existe o está borrado (borrado lógico).
 */
public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public ResourceNotFoundException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode, cause, args);
    }
}
