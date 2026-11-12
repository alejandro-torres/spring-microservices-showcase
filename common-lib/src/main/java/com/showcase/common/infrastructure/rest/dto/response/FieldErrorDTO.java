package com.showcase.common.infrastructure.rest.dto.response;

/**
 * Validation error of a specific request field.
 */
public record FieldErrorDTO(String field, String message) {
}
