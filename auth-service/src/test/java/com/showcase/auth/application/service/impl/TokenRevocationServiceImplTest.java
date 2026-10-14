package com.showcase.auth.application.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.showcase.auth.application.security.TokenPolicy;
import com.showcase.auth.application.service.SecurityAuditService;
import com.showcase.auth.domain.model.SecurityEventType;
import com.showcase.auth.domain.repository.RevokedTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TokenRevocationServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-14T12:00:00.700Z");

    private final RevokedTokenRepository repository = mock(RevokedTokenRepository.class);
    private final SecurityAuditService auditService = mock(SecurityAuditService.class);
    private final TokenRevocationServiceImpl service = new TokenRevocationServiceImpl(repository, auditService,
            new TokenPolicy(Duration.ofMinutes(15)), Clock.fixed(NOW, ZoneOffset.UTC));

    @Nested
    @DisplayName("revoke")
    class Revoke {

        @Test
        @DisplayName("revoca el token solo durante el tiempo que le queda de vida y lo audita")
        void revoke_shouldUseRemainingLifetime() {
            service.revoke("jti-1", NOW.plus(Duration.ofMinutes(10)), "ana");

            verify(repository).revoke("jti-1", Duration.ofMinutes(10));
            verify(auditService).record(SecurityEventType.TOKEN_REVOKED, "ana", "logout");
        }

        @Test
        @DisplayName("no guarda nada para un token ya caducado o sin identificador")
        void revoke_expiredOrWithoutId_shouldDoNothing() {
            service.revoke("jti-1", NOW.minusSeconds(1), "ana");
            service.revoke(null, NOW.plusSeconds(60), "ana");

            verify(repository, never()).revoke(any(), any());
        }
    }

    @Nested
    @DisplayName("revokeAllForUser e isRevoked")
    class RevokeAll {

        @Test
        @DisplayName("guarda el instante en segundos (la precisión de iat) con la vida de un token")
        void revokeAllForUser_shouldTruncateToSeconds() {
            service.revokeAllForUser("ana", "password changed");

            verify(repository).revokeIssuedBefore("ana", Instant.parse("2026-10-14T12:00:00Z"), Duration.ofMinutes(15));
            verify(auditService).record(SecurityEventType.TOKEN_REVOKED, "ana", "all tokens: password changed");
        }

        @Test
        @DisplayName("un token está revocado si su jti lo está o si se emitió hasta el instante de revocación")
        void isRevoked_shouldCheckTokenIdAndIssueDate() {
            var revokedAt = Instant.parse("2026-10-14T12:00:00Z");
            given(repository.isRevoked("revocado")).willReturn(true);
            given(repository.revokedBefore("ana")).willReturn(Optional.of(revokedAt));

            assertThat(service.isRevoked("revocado", "bea", revokedAt)).isTrue();
            assertThat(service.isRevoked("jti", "ana", revokedAt.minusSeconds(30))).isTrue();
            assertThat(service.isRevoked("jti", "ana", revokedAt)).isTrue();
            assertThat(service.isRevoked("jti", "ana", revokedAt.plusSeconds(1))).isFalse();
            assertThat(service.isRevoked("jti", "bea", revokedAt)).isFalse();
        }
    }
}
