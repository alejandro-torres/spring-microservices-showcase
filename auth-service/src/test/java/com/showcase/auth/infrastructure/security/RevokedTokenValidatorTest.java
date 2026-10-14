package com.showcase.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.showcase.auth.application.service.TokenRevocationService;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class RevokedTokenValidatorTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-10-14T12:00:00Z");

    private final TokenRevocationService revocationService = mock(TokenRevocationService.class);
    private final RevokedTokenValidator validator = new RevokedTokenValidator(revocationService);

    private static Jwt jwt() {
        return Jwt.withTokenValue("token").header("alg", "RS256").subject("ana").jti("jti-1")
                .issuedAt(ISSUED_AT).expiresAt(ISSUED_AT.plusSeconds(900)).build();
    }

    @Test
    @DisplayName("acepta un token no revocado y rechaza uno revocado con invalid_token")
    void validate_shouldCheckRevocation() {
        given(revocationService.isRevoked("jti-1", "ana", ISSUED_AT)).willReturn(false, true);

        assertThat(validator.validate(jwt()).hasErrors()).isFalse();
        var result = validator.validate(jwt());
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).singleElement()
                .satisfies(error -> assertThat(error.getErrorCode()).isEqualTo("invalid_token"));
    }

    @Test
    @DisplayName("si Redis no responde rechaza el token (fail closed)")
    void validate_whenStoreUnavailable_shouldReject() {
        given(revocationService.isRevoked("jti-1", "ana", ISSUED_AT))
                .willThrow(new IllegalStateException("Redis no disponible"));

        assertThat(validator.validate(jwt()).hasErrors()).isTrue();
    }
}
