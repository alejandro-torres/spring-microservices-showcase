package com.showcase.order.testdata;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/**
 * Tokens JWT de prueba con las mismas autoridades que produce el JwtAuthoritiesConverter.
 */
public final class TestJwt {

    private TestJwt() {
    }

    /** ADMIN con lectura y escritura de pedidos. */
    public static JwtRequestPostProcessor admin() {
        return jwt().jwt(token -> token.subject("admin")).authorities(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("order:read"),
                new SimpleGrantedAuthority("order:write"));
    }

    /** Usuario sin rol ADMIN que puede gestionar pedidos. */
    public static JwtRequestPostProcessor manager() {
        return jwt().jwt(token -> token.subject("gestor")).authorities(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("order:read"),
                new SimpleGrantedAuthority("order:write"));
    }

    /** Usuario que solo puede consultar pedidos. */
    public static JwtRequestPostProcessor reader() {
        return jwt().jwt(token -> token.subject("lector")).authorities(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("order:read"));
    }
}
