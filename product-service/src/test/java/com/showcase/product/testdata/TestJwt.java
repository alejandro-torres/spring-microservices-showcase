package com.showcase.product.testdata;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/**
 * Tokens JWT de prueba con las mismas autoridades que produce el JwtAuthoritiesConverter.
 */
public final class TestJwt {

    private TestJwt() {
    }

    /** ADMIN con lectura y escritura del catálogo. */
    public static JwtRequestPostProcessor admin() {
        return jwt().jwt(token -> token.subject("admin")).authorities(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("product:read"),
                new SimpleGrantedAuthority("product:write"));
    }

    /** Cliente de la tienda (rol USER): solo puede consultar el catálogo. */
    public static JwtRequestPostProcessor customer() {
        return jwt().jwt(token -> token.subject("cliente")).authorities(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("product:read"));
    }

    /** Gestor del catálogo sin rol ADMIN. */
    public static JwtRequestPostProcessor catalogManager() {
        return jwt().jwt(token -> token.subject("gestor")).authorities(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("product:read"),
                new SimpleGrantedAuthority("product:write"));
    }
}
