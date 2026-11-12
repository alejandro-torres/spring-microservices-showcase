package com.showcase.common.domain.exception;

/**
 * Stable error code carried in the API responses. Each service defines its own with an enum
 * (e.g. {@code ProductErrorCode}) in addition to {@link CommonErrorCode}.
 */
public interface ErrorCode {

    /** Identifier the client receives in the {@code code} field; not translated. */
    String code();

    ErrorCategory category();

    /** Key of the translated message in the {@code messages_*.properties} files. */
    default String messageKey() {
        return "error." + code();
    }
}
