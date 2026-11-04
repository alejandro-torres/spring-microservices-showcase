package com.showcase.common.domain.exception;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Base business exception. It carries an {@link ErrorCode} and the message parameters;
 * the text the client sees is resolved later, translated, from {@link ErrorCode#messageKey()}.
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

    // Technical message for the logs, not for the client.
    private static String buildMessage(ErrorCode errorCode, Object[] args) {
        Objects.requireNonNull(errorCode, "errorCode");
        return args.length == 0 ? errorCode.code() : errorCode.code() + " " + Arrays.toString(args);
    }
}
