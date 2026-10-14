package com.showcase.auth.infrastructure.security;

import com.showcase.common.infrastructure.security.JwtAuthoritiesConverter;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

/**
 * Añade al token de acceso de un usuario sus roles y permisos ({@code roles}, {@code permissions}),
 * para que cualquier servicio pueda autorizar con {@code @PreAuthorize} sin consultar a auth-service.
 * Los tokens entre servicios (client_credentials) solo llevan el scope.
 */
@Component
public class JwtTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private static final String ROLE_PREFIX = "ROLE_";
    // Spring Security 7 añade autoridades FACTOR_* (factor de autenticación usado); no son permisos.
    private static final String FACTOR_PREFIX = "FACTOR_";

    @Override
    public void customize(JwtEncodingContext context) {
        if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())
                || AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())) {
            return;
        }
        Collection<? extends GrantedAuthority> authorities = context.getPrincipal().getAuthorities();
        List<String> roles = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .sorted()
                .toList();
        List<String> permissions = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> !authority.startsWith(ROLE_PREFIX) && !authority.startsWith(FACTOR_PREFIX))
                .sorted()
                .toList();
        context.getClaims()
                .claim(JwtAuthoritiesConverter.ROLES_CLAIM, roles)
                .claim(JwtAuthoritiesConverter.PERMISSIONS_CLAIM, permissions);
    }
}
