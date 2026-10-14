package com.showcase.order.infrastructure.metrics.impl;

import com.showcase.common.infrastructure.metrics.AfterCommit;
import com.showcase.order.application.metrics.OrderMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Contador {@code showcase.orders} con la etiqueta {@code event} (created, confirmed, rejected, cancelled) y, en
 * los rechazados, {@code reason}. Se incrementa después del commit: un pedido cuya transacción falla no cuenta.
 */
@Component
public class MicrometerOrderMetricsImpl implements OrderMetrics {

    static final String NAME = "showcase.orders";

    private final MeterRegistry registry;

    public MicrometerOrderMetricsImpl(MeterRegistry registry) {
        this.registry = registry;
    }

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
                .description("Pedidos por evento del ciclo de vida")
                .tag("event", event)
                .tag("reason", reason)
                .register(registry);
        AfterCommit.run(counter::increment);
    }
}
