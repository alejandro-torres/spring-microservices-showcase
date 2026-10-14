package com.showcase.order.infrastructure.client.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Reenvía el JWT del usuario a los otros servicios, que aplican sus propios permisos (p. ej. product:read).
 * Lee el contexto de seguridad del hilo actual, así que las llamadas Feign deben hacerse en el mismo hilo
 * que la petición (por eso el circuit breaker se ejecuta de forma síncrona).
 */
public class BearerTokenRelayInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt) {
            template.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getToken().getTokenValue());
        }
    }
}
