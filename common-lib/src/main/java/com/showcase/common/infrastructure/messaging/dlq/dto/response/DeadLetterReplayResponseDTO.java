package com.showcase.common.infrastructure.messaging.dlq.dto.response;

public record DeadLetterReplayResponseDTO(String queue, int replayed) {
}
