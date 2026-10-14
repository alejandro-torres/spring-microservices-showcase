package com.showcase.common.domain.exception;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Excepción base de negocio. Lleva un {@link ErrorCode} y los parámetros del mensaje;
 * el texto que ve el cliente se resuelve después, traducido, a partir de {@link ErrorCode#messageKey()}.
 */
public abstract class DomainException extends RuntimeException {

    private final transient ErrorCode errorCode;
    private final transient List<Object> args;

    protected DomainException(ErrorCode errorCode, Object... args) {
        this(errorCode, null, args);
    }

    protected DomainException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(buildMessage(errorCode, args), cause);
        this.errorCode = errorCode;
        this.args = Collections.unmodifiableList(Arrays.asList(args.clone()));
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public List<Object> getArgs() {
        return args;
    }

    // Mensaje técnico para los logs, no para el cliente.
    private static String buildMessage(ErrorCode errorCode, Object[] args) {
        Objects.requireNonNull(errorCode, "errorCode");
        return args.length == 0 ? errorCode.code() : errorCode.code() + " " + Arrays.toString(args);
    }
}
