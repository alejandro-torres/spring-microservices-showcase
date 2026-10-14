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
 * Clientes Feign hacia product-service y customer-service. Los timeouts están en
 * {@code spring.cloud.openfeign.client.config} y el circuit breaker en {@code showcase.clients.circuit-breaker}.
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
     * 401/403 no son fallos del servicio remoto (son del token del usuario): no deben abrir el circuito.
     * Los 404 tampoco, porque los clientes los convierten en un Optional vacío dentro de la llamada protegida.
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

    /** Estado de cada circuit breaker (cerrado, abierto, medio abierto) y sus llamadas, para Prometheus. */
    @Bean
    public MeterBinder circuitBreakerMetrics(CircuitBreakerRegistry circuitBreakerRegistry) {
        return TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(circuitBreakerRegistry);
    }
}
