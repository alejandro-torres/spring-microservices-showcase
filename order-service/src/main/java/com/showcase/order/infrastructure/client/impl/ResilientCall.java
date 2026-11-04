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
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a Feign call protected by a circuit breaker, on the same thread (the user's token is in the thread's
 * security context). A 404 is returned as an empty Optional and counts as a successful call.
 * Open circuit, timeouts and network errors become ExternalServiceException (503).
 */
final class ResilientCall {

    private static final Logger log = LoggerFactory.getLogger(ResilientCall.class);

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
            log.warn("Circuit {} open: the service is not called", circuitBreaker.getName());
            throw new ExternalServiceException(CommonErrorCode.SERVICE_UNAVAILABLE, open);
        } catch (FeignException feignException) {
            log.warn("Failure calling {}: {}", circuitBreaker.getName(), feignException.getMessage());
            throw new ExternalServiceException(CommonErrorCode.SERVICE_UNAVAILABLE, feignException);
        }
    }
}
