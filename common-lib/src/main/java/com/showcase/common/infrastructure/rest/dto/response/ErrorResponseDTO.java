package com.showcase.common.infrastructure.rest.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * Formato común de todas las respuestas de error de la API.
 *
 * @param timestamp momento del error
 * @param status    código HTTP
 * @param error     texto estándar del código HTTP (p. ej. "Not Found")
 * @param code      código de error estable para el cliente; no se traduce
 * @param message   mensaje traducido al idioma de la petición
 * @param path      ruta de la petición
 * @param traceId   identificador de la traza para buscarla en Zipkin y en los logs
 * @param errors    errores por campo; solo en los errores de validación
 */
public record ErrorResponseDTO(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        @JsonInclude(JsonInclude.Include.NON_NULL) String traceId,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<FieldErrorDTO> errors) {

    public ErrorResponseDTO {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}
