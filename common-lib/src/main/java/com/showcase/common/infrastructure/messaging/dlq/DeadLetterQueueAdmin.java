package com.showcase.common.infrastructure.messaging.dlq;

import java.util.List;

/**
 * Lookup and re-sending of the messages that ended up in the service's DLQs. Only the DLQs of the queues the
 * service itself consumes can be operated on; any other returns 404.
 */
public interface DeadLetterQueueAdmin {

    List<DeadLetterQueue> queues();

    /** Reads up to {@code limit} messages without taking them off the queue. */
    List<DeadLetterMessage> peek(String deadLetterQueue, int limit);

    /**
     * Returns up to {@code max} messages to their original queue to be processed again.
     *
     * @return how many messages were re-sent
     */
    int replay(String deadLetterQueue, int max);

    record DeadLetterQueue(String name, String sourceQueue, long messageCount) {
    }

    /**
     * @param reason rejection reason that RabbitMQ adds in the {@code x-death} header ({@code rejected},
     *               {@code expired}...)
     */
    record DeadLetterMessage(String messageId, String eventType, String reason, long deathCount, String body) {
    }
}
