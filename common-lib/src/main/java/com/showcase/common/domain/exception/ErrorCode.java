package com.showcase.common.domain.exception;

/**
 * Código de error estable que viaja en las respuestas de la API. Cada servicio
 * define el suyo con un enum (p. ej. {@code ProductErrorCode}) además de {@link CommonErrorCode}.
 */
public interface ErrorCode {

    /** Identificador que recibe el cliente en el campo {@code code}; no se traduce. */
    String code();

    ErrorCategory category();

    /** Clave del mensaje traducido en los ficheros {@code messages_*.properties}. */
    default String messageKey() {
        return "error." + code();
    }
}
