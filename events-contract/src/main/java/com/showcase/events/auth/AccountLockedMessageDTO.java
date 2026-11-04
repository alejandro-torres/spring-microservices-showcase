package com.showcase.events.auth;

import java.time.Instant;

/** Account locked by failed login attempts until {@code lockedUntil}. */
public record AccountLockedMessageDTO(Long userId, String username, Instant lockedUntil) {
}
