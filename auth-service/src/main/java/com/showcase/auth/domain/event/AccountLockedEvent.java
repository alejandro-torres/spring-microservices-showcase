package com.showcase.auth.domain.event;

import java.time.Instant;

/** The account has been locked by failed login attempts until {@code lockedUntil}. */
public record AccountLockedEvent(Long userId, String username, Instant lockedUntil, Instant occurredAt) {
}
