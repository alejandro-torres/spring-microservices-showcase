package com.showcase.common.infrastructure.security;

import com.showcase.common.domain.exception.ErrorCode;
import com.showcase.common.infrastructure.exception.ErrorResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.LocaleResolver;
import tools.jackson.databind.json.JsonMapper;

/**
 * Escribe un {@code ErrorResponseDTO} directamente en la respuesta. Lo usan los filtros de Spring
 * Security, que se ejecutan antes que Spring MVC y por eso no pasan por el GlobalExceptionHandler.
 */
@RequiredArgsConstructor
public class SecurityErrorResponseWriter {

    private final ErrorResponseFactory responses;
    private final JsonMapper jsonMapper;
    private final LocaleResolver localeResolver;

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                      ErrorCode errorCode) throws IOException {
        var body = responses.body(status, errorCode, List.of(), List.of(), request,
                localeResolver.resolveLocale(request));
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(jsonMapper.writeValueAsString(body));
    }
}
