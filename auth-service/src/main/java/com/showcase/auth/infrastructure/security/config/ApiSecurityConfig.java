package com.showcase.auth.infrastructure.security.config;

import com.showcase.common.infrastructure.security.JwtAuthoritiesConverter;
import com.showcase.common.infrastructure.security.RestAccessDeniedHandler;
import com.showcase.common.infrastructure.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * La API REST ({@code /api/**}) funciona como resource server: sin sesión, con un JWT en la cabecera
 * {@code Authorization: Bearer} y permisos comprobados con {@code @PreAuthorize} en los controllers.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class ApiSecurityConfig {

    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, JwtAuthoritiesConverter jwtAuthoritiesConverter,
                                                      RestAuthenticationEntryPoint authenticationEntryPoint,
                                                      RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthoritiesConverter))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Sin cookies de sesión no hay riesgo de CSRF: el token viaja en una cabecera.
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
