package com.showcase.common.infrastructure.security;

import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.infrastructure.exception.ErrorResponseFactory;
import com.showcase.common.infrastructure.rest.dto.response.ErrorResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Errores de seguridad lanzados dentro de los controllers, sobre todo por {@code @PreAuthorize}.
 * Sin este handler, el {@code @ExceptionHandler(Exception.class)} global los convertiría en un 500.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class SecurityExceptionHandler {

    private final ErrorResponseFactory responses;

    /** 403 si el usuario está autenticado; 401 si es anónimo (todavía puede identificarse). */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDTO> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return responses.create(HttpStatus.UNAUTHORIZED, CommonErrorCode.UNAUTHORIZED, List.of(), request);
        }
        return responses.create(HttpStatus.FORBIDDEN, CommonErrorCode.FORBIDDEN, List.of(), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponseDTO> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return responses.create(HttpStatus.UNAUTHORIZED, CommonErrorCode.UNAUTHORIZED, List.of(), request);
    }
}
