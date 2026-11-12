package com.showcase.common.domain.exception;

/**
 * Type of failure from the business point of view. The domain does not know HTTP:
 * the infrastructure does the translation to a status code.
 */
public enum ErrorCategory {
    INVALID_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    LOCKED,
    BUSINESS_RULE,
    RATE_LIMITED,
    EXTERNAL_SERVICE,
    INTERNAL
}
