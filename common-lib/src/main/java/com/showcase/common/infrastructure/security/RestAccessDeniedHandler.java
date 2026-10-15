package com.showcase.common.infrastructure.security;

import com.showcase.common.domain.exception.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Respuesta 403 en el formato común cuando el usuario está autenticado pero no tiene permiso.
 */
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(RestAccessDeniedHandler.class);

    private final SecurityErrorResponseWriter writer;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("Acceso denegado a {} {}", request.getMethod(), request.getRequestURI());
        writer.write(request, response, HttpStatus.FORBIDDEN, CommonErrorCode.FORBIDDEN);
    }
}
