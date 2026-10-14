package com.showcase.common.infrastructure.security;

import com.showcase.common.domain.exception.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Respuesta 401 en el formato común cuando la petición no trae credenciales o el token no es válido.
 */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(RestAuthenticationEntryPoint.class);
    private static final String OAUTH2_EXCEPTION = "org.springframework.security.oauth2.core.OAuth2AuthenticationException";

    private final SecurityErrorResponseWriter writer;

    public RestAuthenticationEntryPoint(SecurityErrorResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        boolean invalidToken = isOAuth2Exception(authException);
        log.debug("Petición no autenticada a {}: {}", request.getRequestURI(), authException.getMessage());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, invalidToken ? "Bearer error=\"invalid_token\"" : "Bearer");
        writer.write(request, response, HttpStatus.UNAUTHORIZED,
                invalidToken ? CommonErrorCode.INVALID_TOKEN : CommonErrorCode.UNAUTHORIZED);
    }

    // Comparación por nombre para no obligar a tener OAuth2 en el classpath de todos los servicios.
    private static boolean isOAuth2Exception(Throwable exception) {
        for (Class<?> type = exception.getClass(); type != null; type = type.getSuperclass()) {
            if (OAUTH2_EXCEPTION.equals(type.getName())) {
                return true;
            }
        }
        return false;
    }
}
