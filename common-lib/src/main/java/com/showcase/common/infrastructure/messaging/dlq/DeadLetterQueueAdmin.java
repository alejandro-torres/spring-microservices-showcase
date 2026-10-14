package com.showcase.common.infrastructure.messaging.dlq;

import java.util.List;

/**
 * Consulta y reenvío de los mensajes que han acabado en las DLQ del servicio. Solo se puede operar sobre las
 * DLQ de las colas que consume el propio servicio; cualquier otra devuelve 404.
 */
public interface DeadLetterQueueAdmin {

    List<DeadLetterQueue> queues();

    /** Lee hasta {@code limit} mensajes sin sacarlos de la cola. */
    List<DeadLetterMessage> peek(String deadLetterQueue, int limit);

    /**
     * Devuelve hasta {@code max} mensajes a su cola original para que se procesen otra vez.
     *
     * @return cuántos mensajes se han reenviado
     */
    int replay(String deadLetterQueue, int max);

    record DeadLetterQueue(String name, String sourceQueue, long messageCount) {
    }

    /**
     * @param reason motivo del rechazo que RabbitMQ añade en la cabecera {@code x-death} ({@code rejected},
     *               {@code expired}...)
     */
    record DeadLetterMessage(String messageId, String eventType, String reason, long deathCount, String body) {
    }
}
