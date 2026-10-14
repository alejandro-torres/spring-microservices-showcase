package com.showcase.auth.domain.event;

import java.time.Instant;

/** La cuenta se ha bloqueado por intentos fallidos de login hasta {@code lockedUntil}. */
public record AccountLockedEvent(Long userId, String username, Instant lockedUntil, Instant occurredAt) {
}
