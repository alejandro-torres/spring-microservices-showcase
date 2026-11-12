package com.showcase.common.infrastructure.exception;

import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.infrastructure.rest.dto.response.ErrorResponseDTO;
import com.showcase.common.infrastructure.rest.dto.response.FieldErrorDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Bean Validation violations thrown outside the controllers (e.g. with {@code @Validated} on a service).
 * Separate because it is only registered if the service includes Bean Validation.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class ConstraintViolationExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ConstraintViolationExceptionHandler.class);
    private static final Set<String> INTERNAL_ATTRIBUTES = Set.of("message", "groups", "payload");

    private final ErrorResponseFactory responses;

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponseDTO> handleConstraintViolation(ConstraintViolationException ex,
                                                                      HttpServletRequest request) {
        List<FieldErrorDTO> errors = ex.getConstraintViolations().stream()
                .map(violation -> new FieldErrorDTO(lastNode(violation), responses.resolve(toResolvable(violation))))
                .toList();
        log.warn("Invalid request at {}: {} validation errors", request.getRequestURI(), errors.size());
        return responses.create(HttpStatus.BAD_REQUEST, CommonErrorCode.VALIDATION_ERROR, List.of(), errors, request);
    }

    private static String lastNode(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }

    // Same format Spring uses in FieldErrors: the field and then the attributes in alphabetical order.
    private static DefaultMessageSourceResolvable toResolvable(ConstraintViolation<?> violation) {
        String constraint = violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
        Map<String, Object> attributes = new TreeMap<>(violation.getConstraintDescriptor().getAttributes());
        INTERNAL_ATTRIBUTES.forEach(attributes::remove);
        List<Object> args = new ArrayList<>();
        args.add(lastNode(violation));
        args.addAll(attributes.values());
        return new DefaultMessageSourceResolvable(new String[]{constraint}, args.toArray(), violation.getMessage());
    }
}
