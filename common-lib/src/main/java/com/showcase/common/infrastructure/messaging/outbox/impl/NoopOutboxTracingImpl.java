package com.showcase.common.infrastructure.messaging.outbox.impl;

import com.showcase.common.infrastructure.messaging.outbox.OutboxTracing;
import java.util.function.Supplier;

/** For services without Micrometer Tracing: the outbox works the same, just without linking the traces. */
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
