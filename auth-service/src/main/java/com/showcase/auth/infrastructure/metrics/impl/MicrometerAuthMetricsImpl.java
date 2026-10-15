package com.showcase.auth.infrastructure.metrics.impl;

import com.showcase.auth.application.metrics.AuthMetrics;
import com.showcase.common.infrastructure.metrics.AfterCommit;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Contadores {@code showcase.auth.registrations}, {@code showcase.auth.logins} (etiqueta {@code result}: success o
 * failure) y {@code showcase.auth.accounts.locked}. Se incrementan después del commit. Micrometer devuelve el mismo
 * contador si ya está registrado con ese nombre y etiquetas.
 */
@Component
@RequiredArgsConstructor
public class MicrometerAuthMetricsImpl implements AuthMetrics {

    private final MeterRegistry registry;

    @Override
    public void userRegistered() {
        increment(Counter.builder("showcase.auth.registrations").description("Usuarios registrados"));
    }

    @Override
    public void loginSucceeded() {
        increment(logins().tag("result", "success"));
    }

    @Override
    public void loginFailed() {
        increment(logins().tag("result", "failure"));
    }

    @Override
    public void accountLocked() {
        increment(Counter.builder("showcase.auth.accounts.locked")
                .description("Cuentas bloqueadas por intentos fallidos"));
    }

    private static Counter.Builder logins() {
        return Counter.builder("showcase.auth.logins").description("Intentos de login por resultado");
    }

    private void increment(Counter.Builder builder) {
        Counter counter = builder.register(registry);
        AfterCommit.run(counter::increment);
    }
}
