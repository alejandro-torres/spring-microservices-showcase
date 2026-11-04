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
 * Gets the IP, user-agent, traceId and authenticated user from the current HTTP request.
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

    // Behind the gateway the real IP arrives in X-Forwarded-For (the first in the list is the client's).
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
