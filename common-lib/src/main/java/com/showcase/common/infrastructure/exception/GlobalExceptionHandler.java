package com.showcase.common.infrastructure.exception;

import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.DomainException;
import com.showcase.common.infrastructure.rest.dto.response.ErrorResponseDTO;
import com.showcase.common.infrastructure.rest.dto.response.FieldErrorDTO;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Convierte cualquier excepción en una respuesta {@link ErrorResponseDTO} con un código HTTP estándar.
 * El cliente nunca recibe detalles internos de Java: el detalle técnico solo va al log, con el traceId.
 *
 * <p>Va con la menor precedencia para que los handlers específicos (datos, validación, seguridad o los
 * de cada servicio) se evalúen antes que su {@code @ExceptionHandler(Exception.class)} de último recurso.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ErrorResponseFactory responses;
    private final ErrorCodeHttpStatusMapper statusMapper;

    public GlobalExceptionHandler(ErrorResponseFactory responses, ErrorCodeHttpStatusMapper statusMapper) {
        this.responses = responses;
        this.statusMapper = statusMapper;
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponseDTO> handleDomain(DomainException ex, HttpServletRequest request) {
        HttpStatus status = statusMapper.toStatus(ex.getErrorCode());
        if (status.is5xxServerError()) {
            log.error("Error de dominio {} en {}", ex.getErrorCode().code(), request.getRequestURI(), ex);
        } else {
            log.warn("Error de dominio {} en {}: {}", ex.getErrorCode().code(), request.getRequestURI(), ex.getMessage());
        }
        return responses.create(status, ex.getErrorCode(), ex.getArgs(), request);
    }

    /** Errores de @Valid en el cuerpo y de binding de objetos (MethodArgumentNotValidException hereda de esta). */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponseDTO> handleBind(BindException ex, HttpServletRequest request) {
        List<FieldErrorDTO> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.add(new FieldErrorDTO(error.getField(), responses.resolve(error))));
        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> errors.add(new FieldErrorDTO(error.getObjectName(), responses.resolve(error))));
        return validationError(errors, request);
    }

    /** Errores de validación en parámetros de los métodos (@RequestParam @Max(100), @PathVariable @Positive...). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponseDTO> handleMethodValidation(HandlerMethodValidationException ex,
                                                                   HttpServletRequest request) {
        List<FieldErrorDTO> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> result.getResolvableErrors().forEach(error -> {
            String field = error instanceof FieldError fieldError
                    ? fieldError.getField()
                    : result.getMethodParameter().getParameterName();
            errors.add(new FieldErrorDTO(field, responses.resolve(error)));
        }));
        return validationError(errors, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDTO> handleNotReadable(HttpMessageNotReadableException ex,
                                                              HttpServletRequest request) {
        log.warn("Cuerpo ilegible en {}: {}", request.getRequestURI(), ex.getMessage());
        return responses.create(HttpStatus.BAD_REQUEST, CommonErrorCode.MALFORMED_REQUEST, List.of(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDTO> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                               HttpServletRequest request) {
        return responses.create(HttpStatus.BAD_REQUEST, CommonErrorCode.INVALID_PARAMETER,
                List.of(ex.getName(), String.valueOf(ex.getValue())), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponseDTO> handleMissingParameter(MissingServletRequestParameterException ex,
                                                                   HttpServletRequest request) {
        return responses.create(HttpStatus.BAD_REQUEST, CommonErrorCode.MISSING_PARAMETER,
                List.of(ex.getParameterName()), request);
    }

    @ExceptionHandler(MissingPathVariableException.class)
    public ResponseEntity<ErrorResponseDTO> handleMissingPathVariable(MissingPathVariableException ex,
                                                                      HttpServletRequest request) {
        return responses.create(HttpStatus.BAD_REQUEST, CommonErrorCode.MISSING_PARAMETER,
                List.of(ex.getVariableName()), request);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ErrorResponseDTO> handleNoEndpoint(Exception ex, HttpServletRequest request) {
        return responses.create(HttpStatus.NOT_FOUND, CommonErrorCode.ENDPOINT_NOT_FOUND,
                List.of(request.getRequestURI()), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponseDTO> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                     HttpServletRequest request) {
        ResponseEntity<ErrorResponseDTO> response = responses.create(HttpStatus.METHOD_NOT_ALLOWED,
                CommonErrorCode.METHOD_NOT_ALLOWED, List.of(ex.getMethod()), request);
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(response.getHeaders());
        if (ex.getSupportedHttpMethods() != null) {
            headers.setAllow(ex.getSupportedHttpMethods());
        }
        return new ResponseEntity<>(response.getBody(), headers, response.getStatusCode());
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErrorResponseDTO> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex,
                                                                HttpServletRequest request) {
        return responses.create(HttpStatus.NOT_ACCEPTABLE, CommonErrorCode.NOT_ACCEPTABLE, List.of(), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponseDTO> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex,
                                                                       HttpServletRequest request) {
        return responses.create(HttpStatus.UNSUPPORTED_MEDIA_TYPE, CommonErrorCode.UNSUPPORTED_MEDIA_TYPE,
                List.of(String.valueOf(ex.getContentType())), request);
    }

    /** Último recurso: cualquier error inesperado sale como 500 genérico, sin detalles de Java. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Error inesperado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return responses.create(HttpStatus.INTERNAL_SERVER_ERROR, CommonErrorCode.INTERNAL_ERROR, List.of(), request);
    }

    private ResponseEntity<ErrorResponseDTO> validationError(List<FieldErrorDTO> errors, HttpServletRequest request) {
        log.warn("Petición no válida en {}: {} errores de validación", request.getRequestURI(), errors.size());
        return responses.create(HttpStatus.BAD_REQUEST, CommonErrorCode.VALIDATION_ERROR, List.of(), errors, request);
    }
}
