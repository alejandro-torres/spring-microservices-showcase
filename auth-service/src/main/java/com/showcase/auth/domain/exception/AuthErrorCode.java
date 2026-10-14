package com.showcase.auth.domain.exception;

import com.showcase.common.domain.exception.ErrorCategory;
import com.showcase.common.domain.exception.ErrorCode;

/**
 * Códigos de error propios de auth-service.
 */
public enum AuthErrorCode implements ErrorCode {

    USER_NOT_FOUND(ErrorCategory.NOT_FOUND),
    ROLE_NOT_FOUND(ErrorCategory.NOT_FOUND),
    PERMISSION_NOT_FOUND(ErrorCategory.NOT_FOUND),
    USERNAME_ALREADY_EXISTS(ErrorCategory.CONFLICT),
    EMAIL_ALREADY_EXISTS(ErrorCategory.CONFLICT),
    ROLE_ALREADY_EXISTS(ErrorCategory.CONFLICT),
    PERMISSION_ALREADY_EXISTS(ErrorCategory.CONFLICT),
    ROLE_IN_USE(ErrorCategory.CONFLICT),
    PERMISSION_IN_USE(ErrorCategory.CONFLICT),
    ROLE_ALREADY_ASSIGNED(ErrorCategory.BUSINESS_RULE),
    ROLE_NOT_ASSIGNED(ErrorCategory.BUSINESS_RULE),
    ACCOUNT_LOCKED(ErrorCategory.LOCKED),
    INVALID_USERNAME(ErrorCategory.INVALID_REQUEST),
    INVALID_EMAIL(ErrorCategory.INVALID_REQUEST),
    INVALID_ROLE_NAME(ErrorCategory.INVALID_REQUEST),
    INVALID_PERMISSION_NAME(ErrorCategory.INVALID_REQUEST);

    private final ErrorCategory category;

    AuthErrorCode(ErrorCategory category) {
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
