package com.showcase.auth.domain.repository;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Outbound port for revoked tokens. Records expire on their own when no longer needed
 * (an expired token is rejected anyway by its expiry date).
 */
public interface RevokedTokenRepository {

    /** Revokes a specific token (jti claim) for the rest of its lifetime. */
    void revoke(String tokenId, Duration ttl);

    boolean isRevoked(String tokenId);

    /** Revokes all the user's tokens issued up to {@code instant}. */
    void revokeIssuedBefore(String username, Instant instant, Duration ttl);

    Optional<Instant> revokedBefore(String username);
}
