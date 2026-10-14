package com.showcase.common.infrastructure.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;

/**
 * Configuración común de las APIs REST como resource server: JWT emitido por auth-service con sus roles
 * y permisos, sin sesión, sin CSRF (el token va en una cabecera) y errores 401/403 en el formato común.
 *
 * <p>Cada servicio define qué rutas protege y cuáles son públicas, y aplica el resto con
 * {@link #customize(HttpSecurity)}.
 */
public class ResourceServerSecurityCustomizer {

    private final JwtAuthoritiesConverter jwtAuthoritiesConverter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    public ResourceServerSecurityCustomizer(JwtAuthoritiesConverter jwtAuthoritiesConverter,
                                            RestAuthenticationEntryPoint authenticationEntryPoint,
                                            RestAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthoritiesConverter = jwtAuthoritiesConverter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

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
