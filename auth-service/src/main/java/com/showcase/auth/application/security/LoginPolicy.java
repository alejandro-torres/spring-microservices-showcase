package com.showcase.auth.application.security;

import java.time.Duration;

/**
 * Account lockout policy.
 *
 * @param maxFailedAttempts consecutive failed attempts that lock the account
 * @param lockDuration      how long the lock lasts; it then expires on its own
 */
public record LoginPolicy(int maxFailedAttempts, Duration lockDuration) {

    public LoginPolicy {
        if (maxFailedAttempts < 1) {
            throw new IllegalArgumentException("maxFailedAttempts must be at least 1");
        }
        if (lockDuration == null || lockDuration.isNegative() || lockDuration.isZero()) {
            throw new IllegalArgumentException("lockDuration must be positive");
        }
    }
}
