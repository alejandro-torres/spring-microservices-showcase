package com.showcase.common.infrastructure.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;

/**
 * Common configuration of the REST APIs as a resource server: JWT issued by auth-service with its roles
 * and permissions, no session, no CSRF (the token goes in a header) and 401/403 errors in the common format.
 *
 * <p>Each service defines which routes it protects and which are public, and applies the rest with
 * {@link #customize(HttpSecurity)}.
 */
@RequiredArgsConstructor
public class ResourceServerSecurityCustomizer {

    private final JwtAuthoritiesConverter jwtAuthoritiesConverter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    public HttpSecurity customize(HttpSecurity http) throws Exception {
        return http
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthoritiesConverter))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable);
    }
}
