package com.showcase.auth.testdata;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/**
 * Tokens JWT de prueba con las mismas autoridades que produce el JwtAuthoritiesConverter a partir
 * de los claims roles y permissions.
 */
public final class TestJwt {

    private TestJwt() {
    }

    /** Rol ADMIN con todos los permisos de gestión de seguridad. */
    public static JwtRequestPostProcessor admin() {
        return jwt().jwt(token -> token.subject("admin")).authorities(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("user:read"), new SimpleGrantedAuthority("user:write"),
                new SimpleGrantedAuthority("role:read"), new SimpleGrantedAuthority("role:write"),
                new SimpleGrantedAuthority("permission:read"), new SimpleGrantedAuthority("permission:write"));
    }

    /** Usuario con permisos concretos pero sin el rol ADMIN. */
    public static JwtRequestPostProcessor withPermissions(String subject, String... permissions) {
        var authorities = new SimpleGrantedAuthority[permissions.length + 1];
        authorities[0] = new SimpleGrantedAuthority("ROLE_USER");
        for (int i = 0; i < permissions.length; i++) {
            authorities[i + 1] = new SimpleGrantedAuthority(permissions[i]);
        }
        return jwt().jwt(token -> token.subject(subject)).authorities(authorities);
    }
}
