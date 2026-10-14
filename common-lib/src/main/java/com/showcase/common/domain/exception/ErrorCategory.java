package com.showcase.common.domain.exception;

/**
 * Tipo de fallo desde el punto de vista del negocio. El dominio no conoce HTTP:
 * la traducción a código de estado la hace la infraestructura.
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
