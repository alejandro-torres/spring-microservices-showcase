package com.showcase.common.infrastructure.exception;

import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.infrastructure.rest.dto.response.ErrorResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
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
 * Errores de acceso a datos. Va aparte del {@link GlobalExceptionHandler} porque solo se registra
 * cuando el servicio tiene Spring Data / JPA (clases de spring-tx en el classpath).
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DataAccessExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(DataAccessExceptionHandler.class);

    private final ErrorResponseFactory responses;

    public DataAccessExceptionHandler(ErrorResponseFactory responses) {
        this.responses = responses;
    }

    /** Bloqueo optimista: otro usuario modificó el registro (@Version distinta). */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponseDTO> handleOptimisticLock(OptimisticLockingFailureException ex,
                                                                 HttpServletRequest request) {
        log.warn("Modificación concurrente en {}: {}", request.getRequestURI(), ex.getMessage());
        return responses.create(HttpStatus.CONFLICT, CommonErrorCode.CONCURRENT_MODIFICATION, List.of(), request);
    }

    /** Restricción de base de datos (clave única, clave foránea...). El mensaje SQL nunca llega al cliente. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDTO> handleDataIntegrity(DataIntegrityViolationException ex,
                                                                HttpServletRequest request) {
        log.warn("Violación de integridad en {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return responses.create(HttpStatus.CONFLICT, CommonErrorCode.DATA_CONFLICT, List.of(), request);
    }
}
