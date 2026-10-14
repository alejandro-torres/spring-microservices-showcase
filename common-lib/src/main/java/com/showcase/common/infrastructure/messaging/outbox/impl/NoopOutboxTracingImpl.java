package com.showcase.common.infrastructure.messaging.outbox.impl;

import com.showcase.common.infrastructure.messaging.outbox.OutboxTracing;
import java.util.function.Supplier;

/** Para los servicios sin Micrometer Tracing: el outbox funciona igual, solo que sin unir las trazas. */
public class NoopOutboxTracingImpl implements OutboxTracing {

    @Override
    public String currentTraceParent() {
        return null;
    }

    @Override
    public <T> T callWithParent(String traceParent, String spanName, Supplier<T> action) {
        return action.get();
    }
}
