package com.showcase.common.infrastructure.exception;

import com.showcase.common.domain.exception.ErrorCategory;
import com.showcase.common.domain.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Translates the category of an {@link ErrorCode} to the response's HTTP code.
 * It is the only place where the error domain meets HTTP.
 */
public class ErrorCodeHttpStatusMapper {

    public HttpStatus toStatus(ErrorCode errorCode) {
        return toStatus(errorCode.category());
    }

    public HttpStatus toStatus(ErrorCategory category) {
        return switch (category) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case LOCKED -> HttpStatus.LOCKED;
            case BUSINESS_RULE -> HttpStatus.UNPROCESSABLE_CONTENT;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case EXTERNAL_SERVICE -> HttpStatus.SERVICE_UNAVAILABLE;
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
