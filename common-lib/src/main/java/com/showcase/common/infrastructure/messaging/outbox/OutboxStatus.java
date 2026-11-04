package com.showcase.common.infrastructure.messaging.outbox;

/** Status of an event in the {@code outbox_event} table. */
public enum OutboxStatus {

    /** Pending send (or retry). */
    PENDING,
    /** RabbitMQ has confirmed that it received it and had a destination queue. */
    SENT,
    /**
     * No queue was receiving the event after several attempts. It stays in the table for review; to
     * re-send it, just set it back to PENDING.
     */
    UNROUTED
}
