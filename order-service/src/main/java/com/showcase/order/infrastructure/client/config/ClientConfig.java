package com.showcase.order.infrastructure.client.config;

import com.showcase.common.domain.exception.ForbiddenException;
import com.showcase.common.domain.exception.UnauthorizedException;
import com.showcase.order.infrastructure.client.feign.BearerTokenRelayInterceptor;
import com.showcase.order.infrastructure.client.feign.RemoteErrorDecoder;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign clients to product-service and customer-service. The timeouts are in
 * {@code spring.cloud.openfeign.client.config} and the circuit breaker in {@code showcase.clients.circuit-breaker}.
 */
@Configuration(proxyBeanMethods = false)
@EnableFeignClients(basePackages = "com.showcase.order.infrastructure.client.feign")
@EnableConfigurationProperties(CircuitBreakerProperties.class)
public class ClientConfig {

    @Bean
    ErrorDecoder remoteErrorDecoder() {
        return new RemoteErrorDecoder();
    }

    @Bean
    RequestInterceptor bearerTokenRelayInterceptor() {
        return new BearerTokenRelayInterceptor();
    }

    /**
     * 401/403 are not failures of the remote service (they come from the user's token): they must not open the circuit.
     * Neither are 404s, because the clients turn them into an empty Optional inside the protected call.
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry(CircuitBreakerProperties properties) {
        return CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(properties.slidingWindowSize())
                .minimumNumberOfCalls(properties.minimumNumberOfCalls())
                .failureRateThreshold(properties.failureRateThreshold())
                .waitDurationInOpenState(properties.waitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(properties.permittedNumberOfCallsInHalfOpenState())
                .ignoreExceptions(UnauthorizedException.class, ForbiddenException.class)
                .build());
    }

    /** State of each circuit breaker (closed, open, half-open) and its calls, for Prometheus. */
    @Bean
    public MeterBinder circuitBreakerMetrics(CircuitBreakerRegistry circuitBreakerRegistry) {
        return TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(circuitBreakerRegistry);
    }
}
