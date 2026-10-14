package com.showcase.common.infrastructure.rest.dto.response;

/**
 * Error de validación de un campo concreto de la petición.
 */
public record FieldErrorDTO(String field, String message) {
}
