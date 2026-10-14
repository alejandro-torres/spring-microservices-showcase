package com.showcase.order.infrastructure.metrics.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MicrometerOrderMetricsImplTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerOrderMetricsImpl metrics = new MicrometerOrderMetricsImpl(registry);

    private double count(String event, String reason) {
        return registry.get("showcase.orders").tag("event", event).tag("reason", reason).counter().count();
    }

    @Nested
    @DisplayName("contadores de pedidos")
    class Counters {

        @Test
        @DisplayName("cuenta cada evento del ciclo de vida por separado")
        void events_shouldBeCountedSeparately() {
            // when
            metrics.orderCreated();
            metrics.orderCreated();
            metrics.orderConfirmed();
            metrics.orderCancelled();

            // then
            assertThat(count("created", "none")).isEqualTo(2);
            assertThat(count("confirmed", "none")).isEqualTo(1);
            assertThat(count("cancelled", "none")).isEqualTo(1);
        }

        @Test
        @DisplayName("los rechazados llevan el motivo como etiqueta")
        void rejected_shouldBeTaggedWithReason() {
            // when
            metrics.orderRejected("INSUFFICIENT_STOCK");
            metrics.orderRejected(null);

            // then
            assertThat(count("rejected", "INSUFFICIENT_STOCK")).isEqualTo(1);
            assertThat(count("rejected", "unknown")).isEqualTo(1);
        }
    }
}
