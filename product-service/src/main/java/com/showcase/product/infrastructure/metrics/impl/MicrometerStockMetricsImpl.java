package com.showcase.product.infrastructure.metrics.impl;

import com.showcase.common.infrastructure.metrics.AfterCommit;
import com.showcase.product.application.metrics.StockMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@code showcase.stock.operations} counter with the {@code result} tag: reserved, insufficient_stock,
 * product_not_found or released. It is incremented after the operation's commit.
 */
@Component
@RequiredArgsConstructor
public class MicrometerStockMetricsImpl implements StockMetrics {

    static final String NAME = "showcase.stock.operations";

    private final MeterRegistry registry;

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
                .description("Stock reservations and returns by result")
                .tag("result", result)
                .register(registry);
        AfterCommit.run(counter::increment);
    }
}
