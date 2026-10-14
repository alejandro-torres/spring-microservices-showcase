package com.showcase.auth.infrastructure.metrics.impl;

import com.showcase.auth.application.metrics.AuthMetrics;
import com.showcase.common.infrastructure.metrics.AfterCommit;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Contadores {@code showcase.auth.registrations}, {@code showcase.auth.logins} (etiqueta {@code result}: success o
 * failure) y {@code showcase.auth.accounts.locked}. Se incrementan después del commit.
 */
@Component
public class MicrometerAuthMetricsImpl implements AuthMetrics {

    private final Counter registrations;
    private final Counter loginSuccesses;
    private final Counter loginFailures;
    private final Counter lockedAccounts;

    public MicrometerAuthMetricsImpl(MeterRegistry registry) {
        this.registrations = Counter.builder("showcase.auth.registrations")
                .description("Usuarios registrados").register(registry);
        this.loginSuccesses = Counter.builder("showcase.auth.logins")
                .description("Intentos de login por resultado").tag("result", "success").register(registry);
        this.loginFailures = Counter.builder("showcase.auth.logins")
                .description("Intentos de login por resultado").tag("result", "failure").register(registry);
        this.lockedAccounts = Counter.builder("showcase.auth.accounts.locked")
                .description("Cuentas bloqueadas por intentos fallidos").register(registry);
    }

    @Override
    public void userRegistered() {
        AfterCommit.run(registrations::increment);
    }

    @Override
    public void loginSucceeded() {
        AfterCommit.run(loginSuccesses::increment);
    }

    @Override
    public void loginFailed() {
        AfterCommit.run(loginFailures::increment);
    }

    @Override
    public void accountLocked() {
        AfterCommit.run(lockedAccounts::increment);
    }
}
