package com.showcase.common.infrastructure.rest.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * Common format of all API error responses.
 *
 * @param timestamp moment of the error
 * @param status    HTTP code
 * @param error     standard text of the HTTP code (e.g. "Not Found")
 * @param code      stable error code for the client; not translated
 * @param message   message translated to the request's language
 * @param path      request path
 * @param traceId   trace identifier to look it up in Zipkin and the logs
 * @param errors    per-field errors; only in validation errors
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
