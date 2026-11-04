package com.showcase.order.infrastructure.client.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * By default: if at least half of the last 10 calls fail (with a minimum of 5), the circuit opens for
 * 10 seconds and calls fail instantly with 503 instead of waiting for the timeout.
 */
@ConfigurationProperties("showcase.clients.circuit-breaker")
public record CircuitBreakerProperties(
        @DefaultValue("10") int slidingWindowSize,
        @DefaultValue("5") int minimumNumberOfCalls,
        @DefaultValue("50") float failureRateThreshold,
        @DefaultValue("10s") Duration waitDurationInOpenState,
        @DefaultValue("2") int permittedNumberOfCallsInHalfOpenState) {
}
