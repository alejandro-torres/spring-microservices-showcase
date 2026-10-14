package com.showcase.common.domain.exception;

/**
 * Códigos de error compartidos por todos los servicios.
 */
public enum CommonErrorCode implements ErrorCode {

    VALIDATION_ERROR(ErrorCategory.INVALID_REQUEST),
    MALFORMED_REQUEST(ErrorCategory.INVALID_REQUEST),
    INVALID_PARAMETER(ErrorCategory.INVALID_REQUEST),
    MISSING_PARAMETER(ErrorCategory.INVALID_REQUEST),
    INVALID_PAGINATION(ErrorCategory.INVALID_REQUEST),
    INVALID_FILTER_FORMAT(ErrorCategory.INVALID_REQUEST),
    FILTER_FIELD_NOT_ALLOWED(ErrorCategory.INVALID_REQUEST),
    FILTER_OPERATOR_NOT_ALLOWED(ErrorCategory.INVALID_REQUEST),
    INVALID_FILTER_VALUE(ErrorCategory.INVALID_REQUEST),
    TOO_MANY_FILTERS(ErrorCategory.INVALID_REQUEST),
    METHOD_NOT_ALLOWED(ErrorCategory.INVALID_REQUEST),
    NOT_ACCEPTABLE(ErrorCategory.INVALID_REQUEST),
    UNSUPPORTED_MEDIA_TYPE(ErrorCategory.INVALID_REQUEST),
    UNAUTHORIZED(ErrorCategory.UNAUTHORIZED),
    FORBIDDEN(ErrorCategory.FORBIDDEN),
    RESOURCE_NOT_FOUND(ErrorCategory.NOT_FOUND),
    ENDPOINT_NOT_FOUND(ErrorCategory.NOT_FOUND),
    DATA_CONFLICT(ErrorCategory.CONFLICT),
    CONCURRENT_MODIFICATION(ErrorCategory.CONFLICT),
    RATE_LIMIT_EXCEEDED(ErrorCategory.RATE_LIMITED),
    SERVICE_UNAVAILABLE(ErrorCategory.EXTERNAL_SERVICE),
    INTERNAL_ERROR(ErrorCategory.INTERNAL);

    private final ErrorCategory category;

    CommonErrorCode(ErrorCategory category) {
        this.category = category;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public ErrorCategory category() {
        return category;
    }
}
