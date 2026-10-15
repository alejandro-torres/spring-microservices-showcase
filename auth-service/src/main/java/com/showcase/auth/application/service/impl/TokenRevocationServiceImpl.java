package com.showcase.auth.application.service.impl;

import com.showcase.auth.application.security.TokenPolicy;
import com.showcase.auth.application.service.SecurityAuditService;
import com.showcase.auth.application.service.TokenRevocationService;
import com.showcase.auth.domain.model.SecurityEventType;
import com.showcase.auth.domain.repository.RevokedTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenRevocationServiceImpl implements TokenRevocationService {

    private final RevokedTokenRepository revokedTokenRepository;
    private final SecurityAuditService auditService;
    private final TokenPolicy tokenPolicy;
    private final Clock clock;

    @Override
    public void revoke(String tokenId, Instant expiresAt, String username) {
        Duration remaining = Duration.between(Instant.now(clock), expiresAt);
        if (tokenId == null || remaining.isNegative() || remaining.isZero()) {
            return;
        }
        revokedTokenRepository.revoke(tokenId, remaining);
        auditService.record(SecurityEventType.TOKEN_REVOKED, username, "logout");
    }

    /**
     * Guarda el instante de la revocación en segundos, la precisión del claim iat. Un token emitido en el
     * mismo segundo también se considera revocado: es preferible pedir un login más que dejar uno válido.
     */
    @Override
    public void revokeAllForUser(String username, String reason) {
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
        revokedTokenRepository.revokeIssuedBefore(username, now, tokenPolicy.accessTokenTtl());
        auditService.record(SecurityEventType.TOKEN_REVOKED, username, "all tokens: " + reason);
    }

    @Override
    public boolean isRevoked(String tokenId, String username, Instant issuedAt) {
        if (tokenId != null && revokedTokenRepository.isRevoked(tokenId)) {
            return true;
        }
        return issuedAt != null && revokedTokenRepository.revokedBefore(username)
                .map(revokedBefore -> !issuedAt.isAfter(revokedBefore))
                .orElse(false);
    }
}
