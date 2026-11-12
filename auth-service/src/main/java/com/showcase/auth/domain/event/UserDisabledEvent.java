package com.showcase.auth.domain.event;

import java.time.Instant;

/** An administrator has deactivated or deleted the user. */
public record UserDisabledEvent(Long userId, String username, Instant occurredAt) {
}
