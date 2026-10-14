package com.showcase.product.infrastructure.metrics.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MicrometerStockMetricsImplTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerStockMetricsImpl metrics = new MicrometerStockMetricsImpl(registry);

    private double count(String result) {
        return registry.get("showcase.stock.operations").tag("result", result).counter().count();
    }

    @Nested
    @DisplayName("contadores de stock")
    class Counters {

        @Test
        @DisplayName("cuenta reservas, fallos por motivo y devoluciones")
        void counters_shouldBeTaggedByResult() {
            // when
            metrics.stockReserved();
            metrics.reservationFailed("INSUFFICIENT_STOCK");
            metrics.reservationFailed(null);
            metrics.stockReleased();

            // then
            assertThat(count("reserved")).isEqualTo(1);
            assertThat(count("insufficient_stock")).isEqualTo(1);
            assertThat(count("unknown")).isEqualTo(1);
            assertThat(count("released")).isEqualTo(1);
        }
    }
}
