package com.showcase.common.infrastructure.messaging.dlq;

import com.showcase.common.infrastructure.messaging.dlq.dto.response.DeadLetterMessageResponseDTO;
import com.showcase.common.infrastructure.messaging.dlq.dto.response.DeadLetterQueueResponseDTO;
import com.showcase.common.infrastructure.messaging.dlq.dto.response.DeadLetterReplayResponseDTO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administration of the service's DLQs (ADMIN only): see which messages have failed and return them to their queue
 * once the problem has been fixed. It is registered by the messaging auto-configuration in the services that
 * consume events.
 */
@RestController
@RequestMapping("/api/v1/admin/dead-letters")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class DeadLetterController {

    private final DeadLetterQueueAdmin admin;

    @GetMapping
    public List<DeadLetterQueueResponseDTO> queues() {
        return admin.queues().stream()
                .map(queue -> new DeadLetterQueueResponseDTO(queue.name(), queue.sourceQueue(), queue.messageCount()))
                .toList();
    }

    /** Shows the first messages without taking them out of the DLQ. */
    @GetMapping("/{queue}/messages")
    public List<DeadLetterMessageResponseDTO> messages(@PathVariable String queue,
                                                       @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        return admin.peek(queue, limit).stream()
                .map(message -> new DeadLetterMessageResponseDTO(message.messageId(), message.eventType(),
                        message.reason(), message.deathCount(), message.body()))
                .toList();
    }

    /** Returns the messages to their original queue; any that cannot be re-sent stay in the DLQ (503). */
    @PostMapping("/{queue}/replay")
    public DeadLetterReplayResponseDTO replay(@PathVariable String queue,
                                              @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int max) {
        return new DeadLetterReplayResponseDTO(queue, admin.replay(queue, max));
    }
}
