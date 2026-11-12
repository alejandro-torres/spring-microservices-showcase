package com.showcase.common.infrastructure.messaging.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@code outbox.events} metric with the outbox events by status: {@code pending} should almost always be at 0
 * (if it grows, RabbitMQ is not receiving the events) and {@code unrouted} are events no queue was receiving and
 * that must be reviewed. Prometheus reads the value on each query (the partial index on pending rows keeps the
 * query cheap).
 */
@RequiredArgsConstructor
public class OutboxMetrics implements MeterBinder {

    private final JdbcClient jdbcClient;

    @Override
    public void bindTo(MeterRegistry registry) {
        register(registry, OutboxStatus.PENDING);
        register(registry, OutboxStatus.UNROUTED);
    }

    private void register(MeterRegistry registry, OutboxStatus status) {
        Gauge.builder("outbox.events", this, metrics -> metrics.count(status))
                .description("Outbox events by status")
                .tag("status", status.name().toLowerCase())
                .register(registry);
    }

    double count(OutboxStatus status) {
        try {
            return jdbcClient.sql("SELECT count(*) FROM outbox_event WHERE status = :status")
                    .param("status", status.name())
                    .query(Long.class).single();
        } catch (RuntimeException e) {
            // Without a database the metric has no value; the health check already reports the problem.
            return Double.NaN;
        }
    }
}
