package com.showcase.order.infrastructure.client.impl;

import com.showcase.order.application.client.ProductClient;
import com.showcase.order.application.client.ProductInfo;
import com.showcase.order.infrastructure.client.dto.ProductClientResponseDTO;
import com.showcase.order.infrastructure.client.feign.ProductFeignClient;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductClientImpl implements ProductClient {

    static final String CIRCUIT_BREAKER = "product-service";

    private final ProductFeignClient feignClient;
    /** The circuit breaker is requested from the registry on each call (it caches it by name). */
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Override
    public Optional<ProductInfo> findById(Long productId) {
        return ResilientCall.findOptional(circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER),
                        () -> feignClient.findById(productId))
                .map(ProductClientImpl::toInfo);
    }

    private static ProductInfo toInfo(ProductClientResponseDTO dto) {
        return new ProductInfo(dto.id(), dto.name(), dto.price(), dto.stock());
    }
}
