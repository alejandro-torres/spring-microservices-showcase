package com.showcase.order.infrastructure.client.impl;

import com.showcase.order.application.client.CustomerClient;
import com.showcase.order.application.client.CustomerInfo;
import com.showcase.order.domain.model.ShippingAddress;
import com.showcase.order.infrastructure.client.dto.CustomerClientResponseDTO;
import com.showcase.order.infrastructure.client.dto.CustomerClientResponseDTO.AddressClientResponseDTO;
import com.showcase.order.infrastructure.client.feign.CustomerFeignClient;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomerClientImpl implements CustomerClient {

    static final String CIRCUIT_BREAKER = "customer-service";

    private final CustomerFeignClient feignClient;
    /** El circuit breaker se pide al registro en cada llamada (lo guarda en caché por nombre). */
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Override
    public Optional<CustomerInfo> findById(Long customerId) {
        return ResilientCall.findOptional(circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER),
                        () -> feignClient.findById(customerId))
                .map(CustomerClientImpl::toInfo);
    }

    private static CustomerInfo toInfo(CustomerClientResponseDTO dto) {
        List<AddressClientResponseDTO> addresses = dto.addresses() == null ? List.of() : dto.addresses();
        Optional<ShippingAddress> main = addresses.stream().filter(AddressClientResponseDTO::main).findFirst()
                .map(address -> new ShippingAddress(address.street(), address.city(), address.postalCode(),
                        address.country()));
        return new CustomerInfo(dto.id(), dto.email(), main);
    }
}
