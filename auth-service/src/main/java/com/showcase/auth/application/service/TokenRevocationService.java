package com.showcase.auth.application.service;

import java.time.Instant;

/**
 * Revocation of JWT tokens before they expire.
 */
public interface TokenRevocationService {

    /** Revokes a specific token, e.g. on logout. */
    void revoke(String tokenId, Instant expiresAt, String username);

    /** Revokes all the tokens issued to the user so far (password change, deactivation...). */
    void revokeAllForUser(String username, String reason);

    boolean isRevoked(String tokenId, String username, Instant issuedAt);
}
