package com.showcase.auth.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Registro inmutable de un evento de seguridad (quién, qué, cuándo y desde dónde).
 *
 * @param username usuario afectado; puede no existir (p. ej. un login con un usuario inventado)
 * @param details  información adicional técnica, nunca contraseñas ni tokens
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
        username = truncate(username == null || username.isBlank() ? "desconocido" : username, MAX_USERNAME);
        userAgent = truncate(userAgent, MAX_USER_AGENT);
        details = truncate(details, MAX_DETAILS);
    }

    public static SecurityAuditEvent of(SecurityEventType type, String username, String ip, String userAgent,
                                        String traceId, String details, Instant occurredAt) {
        return new SecurityAuditEvent(null, type, username, ip, userAgent, traceId, details, occurredAt);
    }

    // Los datos que llegan de fuera (user-agent, username) pueden ser arbitrariamente largos.
    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
