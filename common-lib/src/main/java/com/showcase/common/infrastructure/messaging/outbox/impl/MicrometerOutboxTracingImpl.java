package com.showcase.common.infrastructure.messaging.outbox.impl;

import com.showcase.common.infrastructure.messaging.outbox.OutboxTracing;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;

/**
 * Stores the trace context with the event (W3C {@code traceparent}) and, when sending it, opens a child span of that
 * trace. With that span in the current scope, the {@code RabbitTemplate} observation hangs from it (Micrometer gives
 * preference to the manually opened span over the scheduled task's) and propagates the {@code traceparent} header
 * to the message, so the consumer continues the same trace.
 */
@RequiredArgsConstructor
public class MicrometerOutboxTracingImpl implements OutboxTracing {

    static final String TRACEPARENT = "traceparent";

    private final Tracer tracer;
    private final Propagator propagator;

    @Override
    public String currentTraceParent() {
        TraceContext context = tracer.currentTraceContext().context();
        if (context == null) {
            return null;
        }
        String flags = Boolean.TRUE.equals(context.sampled()) ? "01" : "00";
        return "00-" + context.traceId() + "-" + context.spanId() + "-" + flags;
    }

    @Override
    public <T> T callWithParent(String traceParent, String spanName, Supplier<T> action) {
        if (traceParent == null) {
            return action.get();
        }
        Span span = propagator.extract(Map.of(TRACEPARENT, traceParent), Map::get).name(spanName).start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            return action.get();
        } finally {
            span.end();
        }
    }
}
