package com.showcase.customer.testdata;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/**
 * Test JWT tokens with the same authorities the JwtAuthoritiesConverter produces.
 */
public final class TestJwt {

    private TestJwt() {
    }

    /** ADMIN with read and write access to customers. */
    public static JwtRequestPostProcessor admin() {
        return jwt().jwt(token -> token.subject("admin")).authorities(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("customer:read"),
                new SimpleGrantedAuthority("customer:write"));
    }

    /** User without the ADMIN role who can manage customers. */
    public static JwtRequestPostProcessor manager() {
        return jwt().jwt(token -> token.subject("manager")).authorities(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("customer:read"),
                new SimpleGrantedAuthority("customer:write"));
    }

    /** User who can only view customers. */
    public static JwtRequestPostProcessor reader() {
        return jwt().jwt(token -> token.subject("reader")).authorities(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("customer:read"));
    }
}
