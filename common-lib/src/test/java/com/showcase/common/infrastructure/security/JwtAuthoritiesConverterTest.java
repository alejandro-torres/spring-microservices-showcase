package com.showcase.common.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtAuthoritiesConverterTest {

    private final JwtAuthoritiesConverter converter = new JwtAuthoritiesConverter();

    private static Jwt.Builder jwt() {
        return Jwt.withTokenValue("token").header("alg", "RS256").subject("ana")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900));
    }

    @Test
    @DisplayName("convierte roles en ROLE_*, permisos tal cual y usa el sub como nombre")
    void convert_userToken_shouldMapRolesAndPermissions() {
        var token = converter.convert(jwt()
                .claim("roles", List.of("ADMIN", "USER"))
                .claim("permissions", List.of("user:read", "user:write"))
                .build());

        assertThat(token.getName()).isEqualTo("ana");
        assertThat(token.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER", "user:read", "user:write");
    }

    @Test
    @DisplayName("convierte el scope de los tokens entre servicios en SCOPE_*")
    void convert_clientToken_shouldMapScopes() {
        var fromText = converter.convert(jwt().claim("scope", "internal products.read").build());
        var fromList = converter.convert(jwt().claim("scope", List.of("internal")).build());

        assertThat(fromText.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("SCOPE_internal", "SCOPE_products.read");
        assertThat(fromList.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("SCOPE_internal");
    }

    @Test
    @DisplayName("un token sin roles, permisos ni scope no da ninguna autoridad")
    void convert_withoutClaims_shouldHaveNoAuthorities() {
        assertThat(converter.convert(jwt().build()).getAuthorities()).isEmpty();
    }
}
