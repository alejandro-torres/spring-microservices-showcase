package com.showcase.product.testdata;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/**
 * Test JWT tokens with the same authorities the JwtAuthoritiesConverter produces.
 */
public final class TestJwt {

    private TestJwt() {
    }

    /** ADMIN with read and write access to the catalog. */
    public static JwtRequestPostProcessor admin() {
        return jwt().jwt(token -> token.subject("admin")).authorities(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("product:read"),
                new SimpleGrantedAuthority("product:write"));
    }

    /** Store customer (USER role): can only browse the catalog. */
    public static JwtRequestPostProcessor customer() {
        return jwt().jwt(token -> token.subject("customer")).authorities(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("product:read"));
    }

    /** Catalog manager without the ADMIN role. */
    public static JwtRequestPostProcessor catalogManager() {
        return jwt().jwt(token -> token.subject("manager")).authorities(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("product:read"),
                new SimpleGrantedAuthority("product:write"));
    }
}
