package com.showcase.order.infrastructure.metrics.impl;

import com.showcase.common.infrastructure.metrics.AfterCommit;
import com.showcase.order.application.metrics.OrderMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@code showcase.orders} counter with the {@code event} tag (created, confirmed, rejected, cancelled) and, for
 * rejected ones, {@code reason}. It is incremented after the commit: an order whose transaction fails does not count.
 */
@Component
@RequiredArgsConstructor
public class MicrometerOrderMetricsImpl implements OrderMetrics {

    static final String NAME = "showcase.orders";

    private final MeterRegistry registry;

    @Override
    public void orderCreated() {
        increment("created", "none");
    }

    @Override
    public void orderConfirmed() {
        increment("confirmed", "none");
    }

    @Override
    public void orderRejected(String reason) {
        increment("rejected", reason == null ? "unknown" : reason);
    }

    @Override
    public void orderCancelled() {
        increment("cancelled", "none");
    }

    private void increment(String event, String reason) {
        Counter counter = Counter.builder(NAME)
                .description("Orders by lifecycle event")
                .tag("event", event)
                .tag("reason", reason)
                .register(registry);
        AfterCommit.run(counter::increment);
    }
}
