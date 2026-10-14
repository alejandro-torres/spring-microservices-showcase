package com.showcase.order.infrastructure.client.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Por defecto: si fallan al menos la mitad de las últimas 10 llamadas (con un mínimo de 5), el circuito se
 * abre 10 segundos y las llamadas fallan al instante con 503 en lugar de esperar al timeout.
 */
@ConfigurationProperties("showcase.clients.circuit-breaker")
public record CircuitBreakerProperties(
        @DefaultValue("10") int slidingWindowSize,
        @DefaultValue("5") int minimumNumberOfCalls,
        @DefaultValue("50") float failureRateThreshold,
        @DefaultValue("10s") Duration waitDurationInOpenState,
        @DefaultValue("2") int permittedNumberOfCallsInHalfOpenState) {
}
