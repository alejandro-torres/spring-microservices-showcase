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
 * Administración de las DLQ del servicio (solo ADMIN): ver qué mensajes han fallado y devolverlos a su cola
 * cuando el problema se ha corregido. Lo registra la autoconfiguración de mensajería en los servicios que
 * consumen eventos.
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

    /** Muestra los primeros mensajes sin sacarlos de la DLQ. */
    @GetMapping("/{queue}/messages")
    public List<DeadLetterMessageResponseDTO> messages(@PathVariable String queue,
                                                       @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        return admin.peek(queue, limit).stream()
                .map(message -> new DeadLetterMessageResponseDTO(message.messageId(), message.eventType(),
                        message.reason(), message.deathCount(), message.body()))
                .toList();
    }

    /** Devuelve los mensajes a su cola original; si alguno no se puede reenviar, se queda en la DLQ (503). */
    @PostMapping("/{queue}/replay")
    public DeadLetterReplayResponseDTO replay(@PathVariable String queue,
                                              @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int max) {
        return new DeadLetterReplayResponseDTO(queue, admin.replay(queue, max));
    }
}
