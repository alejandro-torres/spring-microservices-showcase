package com.showcase.auth.application.security;

import java.time.Duration;

/**
 * Política de bloqueo de cuentas.
 *
 * @param maxFailedAttempts intentos fallidos seguidos que bloquean la cuenta
 * @param lockDuration      tiempo que dura el bloqueo; después caduca solo
 */
public record LoginPolicy(int maxFailedAttempts, Duration lockDuration) {

    public LoginPolicy {
        if (maxFailedAttempts < 1) {
            throw new IllegalArgumentException("maxFailedAttempts debe ser al menos 1");
        }
        if (lockDuration == null || lockDuration.isNegative() || lockDuration.isZero()) {
            throw new IllegalArgumentException("lockDuration debe ser positiva");
        }
    }
}
