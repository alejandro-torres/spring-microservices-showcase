package com.showcase.common.infrastructure.messaging.dlq.dto.response;

public record DeadLetterQueueResponseDTO(String name, String sourceQueue, long messageCount) {
}
