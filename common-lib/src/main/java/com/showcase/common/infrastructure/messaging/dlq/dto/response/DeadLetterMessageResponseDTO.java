package com.showcase.common.infrastructure.messaging.dlq.dto.response;

/** {@code body} is the message's JSON as it arrived, to be able to see why it failed. */
public record DeadLetterMessageResponseDTO(String messageId, String eventType, String reason, long deathCount,
                                           String body) {
}
