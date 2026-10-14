package com.showcase.auth.application.service;

import java.time.Instant;

/**
 * Revocación de tokens JWT antes de que caduquen.
 */
public interface TokenRevocationService {

    /** Revoca un token concreto, p. ej. al cerrar sesión. */
    void revoke(String tokenId, Instant expiresAt, String username);

    /** Revoca todos los tokens emitidos hasta ahora al usuario (cambio de contraseña, desactivación...). */
    void revokeAllForUser(String username, String reason);

    boolean isRevoked(String tokenId, String username, Instant issuedAt);
}
