package com.showcase.order.testdata;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/**
 * Test JWT tokens with the same authorities the JwtAuthoritiesConverter produces.
 */
public final class TestJwt {

    private TestJwt() {
    }

    /** ADMIN with read and write access to orders. */
    public static JwtRequestPostProcessor admin() {
        return jwt().jwt(token -> token.subject("admin")).authorities(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("order:read"),
                new SimpleGrantedAuthority("order:write"));
    }

    /** User without the ADMIN role who can manage orders. */
    public static JwtRequestPostProcessor manager() {
        return jwt().jwt(token -> token.subject("manager")).authorities(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("order:read"),
                new SimpleGrantedAuthority("order:write"));
    }

    /** User who can only view orders. */
    public static JwtRequestPostProcessor reader() {
        return jwt().jwt(token -> token.subject("reader")).authorities(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("order:read"));
    }
}
