package com.showcase.auth.infrastructure.security.impl;

import com.showcase.auth.application.security.RequestMetadata;
import com.showcase.auth.application.security.RequestMetadataProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Obtiene IP, user-agent, traceId y usuario autenticado de la petición HTTP en curso.
 */
@Component
public class ServletRequestMetadataProviderImpl implements RequestMetadataProvider {

    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final String TRACE_ID_KEY = "traceId";

    @Override
    public RequestMetadata current() {
        String actor = currentActor();
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return new RequestMetadata(null, null, MDC.get(TRACE_ID_KEY), actor);
        }
        HttpServletRequest request = attributes.getRequest();
        return new RequestMetadata(clientIp(request), request.getHeader(HttpHeaders.USER_AGENT),
                MDC.get(TRACE_ID_KEY), actor);
    }

    // Detrás del gateway la IP real llega en X-Forwarded-For (la primera de la lista es la del cliente).
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader(FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getName();
    }
}
