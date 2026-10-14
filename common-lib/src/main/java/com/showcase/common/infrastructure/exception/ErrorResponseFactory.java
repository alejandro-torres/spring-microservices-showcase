package com.showcase.common.infrastructure.exception;

import com.showcase.common.domain.exception.ErrorCode;
import com.showcase.common.infrastructure.i18n.ErrorMessageResolver;
import com.showcase.common.infrastructure.rest.dto.response.ErrorResponseDTO;
import com.showcase.common.infrastructure.rest.dto.response.FieldErrorDTO;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.slf4j.MDC;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Construye las respuestas {@link ErrorResponseDTO} con el mensaje traducido, la ruta y el traceId.
 * La comparten el {@link GlobalExceptionHandler} y los handlers de seguridad.
 */
public class ErrorResponseFactory {

    /** Clave del MDC donde Micrometer Tracing deja el identificador de la traza. */
    static final String TRACE_ID_KEY = "traceId";

    private final ErrorMessageResolver messageResolver;
    private final Clock clock;

    public ErrorResponseFactory(ErrorMessageResolver messageResolver, Clock clock) {
        this.messageResolver = messageResolver;
        this.clock = clock;
    }

    public ResponseEntity<ErrorResponseDTO> create(HttpStatus status, ErrorCode errorCode, List<Object> args,
                                                   HttpServletRequest request) {
        return create(status, errorCode, args, List.of(), request);
    }

    public ResponseEntity<ErrorResponseDTO> create(HttpStatus status, ErrorCode errorCode, List<Object> args,
                                                   List<FieldErrorDTO> fieldErrors, HttpServletRequest request) {
        Locale locale = LocaleContextHolder.getLocale();
        var body = new ErrorResponseDTO(
                Instant.now(clock),
                status.value(),
                status.getReasonPhrase(),
                errorCode.code(),
                messageResolver.resolve(errorCode, args, locale),
                request.getRequestURI(),
                MDC.get(TRACE_ID_KEY),
                fieldErrors);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    /** Traduce el mensaje de un error de validación al idioma de la petición. */
    public String resolve(MessageSourceResolvable error) {
        return messageResolver.resolve(error, LocaleContextHolder.getLocale());
    }
}
