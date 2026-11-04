package com.showcase.auth.infrastructure.metrics.impl;

import com.showcase.auth.application.metrics.AuthMetrics;
import com.showcase.common.infrastructure.metrics.AfterCommit;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@code showcase.auth.registrations}, {@code showcase.auth.logins} ({@code result} tag: success or failure) and
 * {@code showcase.auth.accounts.locked} counters. They are incremented after the commit. Micrometer returns the same
 * counter if already registered with that name and tags.
 */
@Component
@RequiredArgsConstructor
public class MicrometerAuthMetricsImpl implements AuthMetrics {

    private final MeterRegistry registry;

    @Override
    public void userRegistered() {
        increment(Counter.builder("showcase.auth.registrations").description("Registered users"));
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
                .description("Accounts locked by failed attempts"));
    }

    private static Counter.Builder logins() {
        return Counter.builder("showcase.auth.logins").description("Login attempts by result");
    }

    private void increment(Counter.Builder builder) {
        Counter counter = builder.register(registry);
        AfterCommit.run(counter::increment);
    }
}
