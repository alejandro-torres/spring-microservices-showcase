package com.showcase.auth.testdata;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/**
 * Test JWT tokens with the same authorities the JwtAuthoritiesConverter produces from
 * the roles and permissions claims.
 */
public final class TestJwt {

    private TestJwt() {
    }

    /** ADMIN role with all the security management permissions. */
    public static JwtRequestPostProcessor admin() {
        return jwt().jwt(token -> token.subject("admin")).authorities(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("user:read"), new SimpleGrantedAuthority("user:write"),
                new SimpleGrantedAuthority("role:read"), new SimpleGrantedAuthority("role:write"),
                new SimpleGrantedAuthority("permission:read"), new SimpleGrantedAuthority("permission:write"));
    }

    /** User with specific permissions but without the ADMIN role. */
    public static JwtRequestPostProcessor withPermissions(String subject, String... permissions) {
        var authorities = new SimpleGrantedAuthority[permissions.length + 1];
        authorities[0] = new SimpleGrantedAuthority("ROLE_USER");
        for (int i = 0; i < permissions.length; i++) {
            authorities[i + 1] = new SimpleGrantedAuthority(permissions[i]);
        }
        return jwt().jwt(token -> token.subject(subject)).authorities(authorities);
    }
}
