package com.showcase.auth.infrastructure.metrics.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MicrometerAuthMetricsImplTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerAuthMetricsImpl metrics = new MicrometerAuthMetricsImpl(registry);

    @Nested
    @DisplayName("contadores de seguridad")
    class Counters {

        @Test
        @DisplayName("cuenta registros, logins por resultado y cuentas bloqueadas")
        void counters_shouldBeIncremented() {
            // when
            metrics.userRegistered();
            metrics.loginSucceeded();
            metrics.loginFailed();
            metrics.loginFailed();
            metrics.accountLocked();

            // then
            assertThat(registry.get("showcase.auth.registrations").counter().count()).isEqualTo(1);
            assertThat(registry.get("showcase.auth.logins").tag("result", "success").counter().count()).isEqualTo(1);
            assertThat(registry.get("showcase.auth.logins").tag("result", "failure").counter().count()).isEqualTo(2);
            assertThat(registry.get("showcase.auth.accounts.locked").counter().count()).isEqualTo(1);
        }
    }
}
