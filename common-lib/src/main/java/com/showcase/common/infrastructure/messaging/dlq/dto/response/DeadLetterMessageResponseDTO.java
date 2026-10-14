package com.showcase.common.infrastructure.messaging.dlq.dto.response;

/** {@code body} es el JSON del mensaje tal como llegó, para poder ver por qué falló. */
public record DeadLetterMessageResponseDTO(String messageId, String eventType, String reason, long deathCount,
                                           String body) {
}
