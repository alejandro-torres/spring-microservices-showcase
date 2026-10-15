package com.showcase.common.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Añade a todas las respuestas la cabecera {@code X-Instance-Id} con la réplica que ha atendido la petición
 * (también a los errores). Sirve para ver cómo reparten el tráfico el gateway y el balanceador.
 */
@RequiredArgsConstructor
public class InstanceIdResponseHeaderFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Instance-Id";

    private final String instanceId;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader(HEADER, instanceId);
        chain.doFilter(request, response);
    }
}
