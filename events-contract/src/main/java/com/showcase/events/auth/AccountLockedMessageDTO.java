package com.showcase.events.auth;

import java.time.Instant;

/** Cuenta bloqueada por intentos fallidos de login hasta {@code lockedUntil}. */
public record AccountLockedMessageDTO(Long userId, String username, Instant lockedUntil) {
}
