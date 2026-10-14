package com.showcase.product.infrastructure.security;

import com.showcase.common.infrastructure.openapi.OpenApiPaths;
import com.showcase.common.infrastructure.security.ResourceServerSecurityCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * product-service es un resource server: valida los JWT de auth-service con su clave pública (JWKS)
 * y autoriza cada endpoint con {@code @PreAuthorize} según los permisos del token.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ResourceServerSecurityCustomizer resourceServer) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                // /actuator/prometheus: solo se publica en la red interna de Docker; el gateway no lo enruta.
                .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers(OpenApiPaths.PUBLIC).permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll());
        return resourceServer.customize(http).build();
    }
}
