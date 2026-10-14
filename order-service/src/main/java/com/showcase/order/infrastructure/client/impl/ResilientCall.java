package com.showcase.order.infrastructure.client.impl;

import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.DomainException;
import com.showcase.common.domain.exception.ExternalServiceException;
import com.showcase.order.infrastructure.client.feign.RemoteResourceNotFoundException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ejecuta una llamada Feign protegida por un circuit breaker, en el mismo hilo (el token del usuario está en
 * el contexto de seguridad del hilo). Un 404 se devuelve como Optional vacío y cuenta como llamada correcta.
 * Circuito abierto, timeouts y errores de red se convierten en ExternalServiceException (503).
 */
final class ResilientCall {

    private static final Logger log = LoggerFactory.getLogger(ResilientCall.class);

    private ResilientCall() {
    }

    static <T> Optional<T> findOptional(CircuitBreaker circuitBreaker, Supplier<T> call) {
        try {
            return circuitBreaker.executeSupplier(() -> {
                try {
                    return Optional.of(call.get());
                } catch (RemoteResourceNotFoundException notFound) {
                    return Optional.empty();
                }
            });
        } catch (DomainException domainException) {
            throw domainException;
        } catch (CallNotPermittedException open) {
            log.warn("Circuito {} abierto: no se llama al servicio", circuitBreaker.getName());
            throw new ExternalServiceException(CommonErrorCode.SERVICE_UNAVAILABLE, open);
        } catch (FeignException feignException) {
            log.warn("Fallo al llamar a {}: {}", circuitBreaker.getName(), feignException.getMessage());
            throw new ExternalServiceException(CommonErrorCode.SERVICE_UNAVAILABLE, feignException);
        }
    }
}
