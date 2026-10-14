package com.showcase.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

class JwtTokenCustomizerTest {

    private final JwtTokenCustomizer customizer = new JwtTokenCustomizer();

    private static JwtEncodingContext context(OAuth2TokenType tokenType, AuthorizationGrantType grantType,
                                              String... authorities) {
        return JwtEncodingContext.with(JwsHeader.with(SignatureAlgorithm.RS256), JwtClaimsSet.builder().subject("ana"))
                .principal(new TestingAuthenticationToken("ana", null, authorities))
                .tokenType(tokenType)
                .authorizationGrantType(grantType)
                .build();
    }

    @Test
    @DisplayName("añade roles sin prefijo y permisos ordenados, ignorando las autoridades FACTOR_*")
    void customize_userAccessToken_shouldAddRolesAndPermissions() {
        var context = context(OAuth2TokenType.ACCESS_TOKEN, AuthorizationGrantType.AUTHORIZATION_CODE,
                "user:write", "ROLE_USER", "FACTOR_PASSWORD", "ROLE_ADMIN", "user:read");

        customizer.customize(context);

        var claims = context.getClaims().build();
        assertThat(claims.getClaimAsStringList("roles")).isEqualTo(List.of("ADMIN", "USER"));
        assertThat(claims.getClaimAsStringList("permissions")).isEqualTo(List.of("user:read", "user:write"));
    }

    @Test
    @DisplayName("no añade nada a los tokens entre servicios (client_credentials)")
    void customize_clientCredentials_shouldNotAddClaims() {
        var context = context(OAuth2TokenType.ACCESS_TOKEN, AuthorizationGrantType.CLIENT_CREDENTIALS, "SCOPE_internal");

        customizer.customize(context);

        assertThat(context.getClaims().build().getClaims()).doesNotContainKeys("roles", "permissions");
    }

    @Test
    @DisplayName("no modifica los refresh tokens")
    void customize_refreshToken_shouldNotAddClaims() {
        var context = context(OAuth2TokenType.REFRESH_TOKEN, AuthorizationGrantType.AUTHORIZATION_CODE, "ROLE_USER");

        customizer.customize(context);

        assertThat(context.getClaims().build().getClaims()).doesNotContainKey("roles");
    }
}
