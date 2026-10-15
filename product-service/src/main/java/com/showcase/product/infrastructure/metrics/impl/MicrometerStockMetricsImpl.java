package com.showcase.product.infrastructure.metrics.impl;

import com.showcase.common.infrastructure.metrics.AfterCommit;
import com.showcase.product.application.metrics.StockMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Contador {@code showcase.stock.operations} con la etiqueta {@code result}: reserved, insufficient_stock,
 * product_not_found o released. Se incrementa después del commit de la operación.
 */
@Component
public class MicrometerStockMetricsImpl implements StockMetrics {

    static final String NAME = "showcase.stock.operations";

    private final MeterRegistry registry;

    public MicrometerStockMetricsImpl(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void stockReserved() {
        increment("reserved");
    }

    @Override
    public void reservationFailed(String reason) {
        increment(reason == null ? "unknown" : reason.toLowerCase());
    }

    @Override
    public void stockReleased() {
        increment("released");
    }

    private void increment(String result) {
        Counter counter = Counter.builder(NAME)
                .description("Reservas y devoluciones de stock por resultado")
                .tag("result", result)
                .register(registry);
        AfterCommit.run(counter::increment);
    }
}
