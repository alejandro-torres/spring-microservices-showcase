package com.showcase.auth.infrastructure.security.config;

import com.showcase.common.infrastructure.openapi.OpenApiPaths;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Default chain: login form of the authorization_code flow and public health endpoints.
 */
@Configuration(proxyBeanMethods = false)
public class WebSecurityConfig {

    @Bean
    @Order(3)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        // /actuator/prometheus: only published on the internal Docker network; the gateway does not
                        // route it.
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus", "/error")
                        .permitAll()
                        .requestMatchers(OpenApiPaths.PUBLIC).permitAll()
                        .anyRequest().authenticated())
                .formLogin(Customizer.withDefaults());
        return http.build();
    }

    /** BCrypt for user passwords and OAuth2 client secrets. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
