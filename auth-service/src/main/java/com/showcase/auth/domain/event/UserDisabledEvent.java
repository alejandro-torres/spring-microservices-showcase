package com.showcase.auth.domain.event;

import java.time.Instant;

/** Un administrador ha desactivado o borrado al usuario. */
public record UserDisabledEvent(Long userId, String username, Instant occurredAt) {
}
