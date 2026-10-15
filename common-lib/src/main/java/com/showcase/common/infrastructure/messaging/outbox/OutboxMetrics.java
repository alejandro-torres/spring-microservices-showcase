package com.showcase.common.infrastructure.messaging.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Métrica {@code outbox.events} con los eventos del outbox por estado: los {@code pending} deberían estar casi
 * siempre a 0 (si crecen, RabbitMQ no está recibiendo los eventos) y los {@code unrouted} son eventos que ninguna
 * cola recibía y hay que revisar. Prometheus lee el valor en cada consulta (el índice parcial de los pendientes
 * hace que la consulta sea barata).
 */
public class OutboxMetrics implements MeterBinder {

    private final JdbcClient jdbcClient;

    public OutboxMetrics(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        register(registry, OutboxStatus.PENDING);
        register(registry, OutboxStatus.UNROUTED);
    }

    private void register(MeterRegistry registry, OutboxStatus status) {
        Gauge.builder("outbox.events", this, metrics -> metrics.count(status))
                .description("Eventos del outbox por estado")
                .tag("status", status.name().toLowerCase())
                .register(registry);
    }

    double count(OutboxStatus status) {
        try {
            return jdbcClient.sql("SELECT count(*) FROM outbox_event WHERE status = :status")
                    .param("status", status.name())
                    .query(Long.class).single();
        } catch (RuntimeException e) {
            // Sin base de datos la métrica no tiene valor; el health check ya avisa del problema.
            return Double.NaN;
        }
    }
}
