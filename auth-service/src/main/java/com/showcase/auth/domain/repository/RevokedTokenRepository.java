package com.showcase.auth.domain.repository;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Puerto de salida para los tokens revocados. Los registros caducan solos cuando ya no hacen falta
 * (un token caducado se rechaza igualmente por su fecha de expiración).
 */
public interface RevokedTokenRepository {

    /** Revoca un token concreto (claim jti) durante el tiempo que le queda de vida. */
    void revoke(String tokenId, Duration ttl);

    boolean isRevoked(String tokenId);

    /** Revoca todos los tokens del usuario emitidos hasta {@code instant}. */
    void revokeIssuedBefore(String username, Instant instant, Duration ttl);

    Optional<Instant> revokedBefore(String username);
}
