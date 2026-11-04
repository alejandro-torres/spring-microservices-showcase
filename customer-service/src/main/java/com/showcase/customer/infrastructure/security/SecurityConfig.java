package com.showcase.customer.infrastructure.security;

import com.showcase.common.infrastructure.openapi.OpenApiPaths;
import com.showcase.common.infrastructure.security.ResourceServerSecurityCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * customer-service is a resource server: it validates auth-service's JWTs with its public key (JWKS)
 * and authorizes each endpoint with {@code @PreAuthorize} according to the token's permissions.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ResourceServerSecurityCustomizer resourceServer) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                // /actuator/prometheus: only published on the internal Docker network; the gateway does not route it.
                .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers(OpenApiPaths.PUBLIC).permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll());
        return resourceServer.customize(http).build();
    }
}
