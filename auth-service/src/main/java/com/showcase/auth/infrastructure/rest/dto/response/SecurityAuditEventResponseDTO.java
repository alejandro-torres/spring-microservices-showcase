package com.showcase.auth.infrastructure.rest.dto.response;

import java.time.Instant;

public record SecurityAuditEventResponseDTO(
        Long id,
        String eventType,
        String username,
        String ip,
        String userAgent,
        String traceId,
        String details,
        Instant occurredAt) {
}
