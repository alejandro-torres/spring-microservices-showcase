package com.showcase.auth.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable record of a security event (who, what, when and from where).
 *
 * @param username affected user; may not exist (e.g. a login with an invented user)
 * @param details  additional technical information, never passwords or tokens
 */
public record SecurityAuditEvent(
        Long id,
        SecurityEventType type,
        String username,
        String ip,
        String userAgent,
        String traceId,
        String details,
        Instant occurredAt) {

    static final int MAX_USERNAME = 100;
    static final int MAX_USER_AGENT = 255;
    static final int MAX_DETAILS = 500;

    public SecurityAuditEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(occurredAt, "occurredAt");
        username = truncate(username == null || username.isBlank() ? "unknown" : username, MAX_USERNAME);
        userAgent = truncate(userAgent, MAX_USER_AGENT);
        details = truncate(details, MAX_DETAILS);
    }

    public static SecurityAuditEvent of(SecurityEventType type, String username, String ip, String userAgent,
                                        String traceId, String details, Instant occurredAt) {
        return new SecurityAuditEvent(null, type, username, ip, userAgent, traceId, details, occurredAt);
    }

    // Data coming from outside (user-agent, username) can be arbitrarily long.
    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
