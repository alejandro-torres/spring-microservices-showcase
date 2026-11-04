package com.showcase.common.infrastructure.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Converts the claims of the JWT issued by auth-service into Spring Security authorities:
 * <ul>
 *   <li>{@code roles: ["ADMIN"]} → {@code ROLE_ADMIN} (for {@code hasRole('ADMIN')})</li>
 *   <li>{@code permissions: ["user:read"]} → {@code user:read} (for {@code hasAuthority('user:read')})</li>
 *   <li>{@code scope: "internal"} → {@code SCOPE_internal} (service-to-service tokens)</li>
 * </ul>
 */
public class JwtAuthoritiesConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String ROLES_CLAIM = "roles";
    public static final String PERMISSIONS_CLAIM = "permissions";
    private static final String SCOPE_CLAIM = "scope";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, authorities(jwt), jwt.getSubject());
    }

    private static Collection<GrantedAuthority> authorities(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        claimValues(jwt, ROLES_CLAIM).forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        claimValues(jwt, PERMISSIONS_CLAIM).forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
        scopes(jwt).forEach(scope -> authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope)));
        return authorities;
    }

    private static List<String> claimValues(Jwt jwt, String claim) {
        List<String> values = jwt.getClaimAsStringList(claim);
        return values == null ? List.of() : values;
    }

    // The scope claim can come as space-separated text or as a list.
    private static List<String> scopes(Jwt jwt) {
        Object scope = jwt.getClaims().get(SCOPE_CLAIM);
        if (scope instanceof String text) {
            return text.isBlank() ? List.of() : List.of(text.trim().split("\\s+"));
        }
        return claimValues(jwt, SCOPE_CLAIM);
    }
}
