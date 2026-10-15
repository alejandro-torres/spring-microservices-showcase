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
 * Guarda el contexto de la traza con el evento (W3C {@code traceparent}) y, al enviarlo, abre un span hijo de esa
 * traza. Con ese span en el ámbito actual, la observación de {@code RabbitTemplate} cuelga de él (Micrometer da
 * preferencia al span abierto a mano frente al de la tarea programada) y propaga la cabecera {@code traceparent}
 * al mensaje, así que el consumidor continúa la misma traza.
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
