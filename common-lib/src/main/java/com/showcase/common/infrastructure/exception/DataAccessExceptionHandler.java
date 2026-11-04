package com.showcase.common.infrastructure.exception;

import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.infrastructure.rest.dto.response.ErrorResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Data access errors. Separate from {@link GlobalExceptionHandler} because it is only registered
 * when the service has Spring Data / JPA (spring-tx classes on the classpath).
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class DataAccessExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(DataAccessExceptionHandler.class);

    private final ErrorResponseFactory responses;

    /** Optimistic locking: another user modified the record (different @Version). */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponseDTO> handleOptimisticLock(OptimisticLockingFailureException ex,
                                                                 HttpServletRequest request) {
        log.warn("Concurrent modification in {}: {}", request.getRequestURI(), ex.getMessage());
        return responses.create(HttpStatus.CONFLICT, CommonErrorCode.CONCURRENT_MODIFICATION, List.of(), request);
    }

    /** Database constraint (unique key, foreign key...). The SQL message never reaches the client. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDTO> handleDataIntegrity(DataIntegrityViolationException ex,
                                                                HttpServletRequest request) {
        log.warn("Integrity violation in {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return responses.create(HttpStatus.CONFLICT, CommonErrorCode.DATA_CONFLICT, List.of(), request);
    }
}
