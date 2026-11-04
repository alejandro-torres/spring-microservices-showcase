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
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Builds the {@link ErrorResponseDTO} responses with the translated message, the path and the traceId.
 * Shared by the {@link GlobalExceptionHandler} and the security handlers.
 */
@RequiredArgsConstructor
public class ErrorResponseFactory {

    /** MDC key where Micrometer Tracing leaves the trace identifier. */
    static final String TRACE_ID_KEY = "traceId";

    private final ErrorMessageResolver messageResolver;
    private final Clock clock;

    public ResponseEntity<ErrorResponseDTO> create(HttpStatus status, ErrorCode errorCode, List<Object> args,
                                                   HttpServletRequest request) {
        return create(status, errorCode, args, List.of(), request);
    }

    public ResponseEntity<ErrorResponseDTO> create(HttpStatus status, ErrorCode errorCode, List<Object> args,
                                                   List<FieldErrorDTO> fieldErrors, HttpServletRequest request) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(body(status, errorCode, args, fieldErrors, request, LocaleContextHolder.getLocale()));
    }

    /**
     * Error response body. Also used by the security filters, which run before Spring MVC and therefore
     * receive the language explicitly.
     */
    public ErrorResponseDTO body(HttpStatus status, ErrorCode errorCode, List<Object> args,
                                 List<FieldErrorDTO> fieldErrors, HttpServletRequest request, Locale locale) {
        return new ErrorResponseDTO(
                Instant.now(clock),
                status.value(),
                status.getReasonPhrase(),
                errorCode.code(),
                messageResolver.resolve(errorCode, args, locale),
                request.getRequestURI(),
                MDC.get(TRACE_ID_KEY),
                fieldErrors);
    }

    /** Translates a validation error's message to the request's language. */
    public String resolve(MessageSourceResolvable error) {
        return messageResolver.resolve(error, LocaleContextHolder.getLocale());
    }
}
