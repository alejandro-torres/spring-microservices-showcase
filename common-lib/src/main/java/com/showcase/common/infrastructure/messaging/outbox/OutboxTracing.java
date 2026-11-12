package com.showcase.common.infrastructure.messaging.outbox;

import java.util.function.Supplier;

/**
 * Keeps the trace across the outbox. The event is stored within the request that originates it, but it is sent
 * later from a scheduled task, which has its own trace: without this, Zipkin would not link the request with the
 * send to RabbitMQ or with what the consumer does afterwards.
 */
public interface OutboxTracing {

    /** Current trace context in W3C {@code traceparent} format, or null if there is no trace. */
    String currentTraceParent();

    /** Performs the send as a child of the trace stored with the event (or with no trace if null). */
    <T> T callWithParent(String traceParent, String spanName, Supplier<T> action);
}
