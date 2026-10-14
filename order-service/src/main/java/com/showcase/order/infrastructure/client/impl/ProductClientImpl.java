package com.showcase.order.infrastructure.client.impl;

import com.showcase.order.application.client.ProductClient;
import com.showcase.order.application.client.ProductInfo;
import com.showcase.order.infrastructure.client.dto.ProductClientResponseDTO;
import com.showcase.order.infrastructure.client.feign.ProductFeignClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ProductClientImpl implements ProductClient {

    private final ProductFeignClient feignClient;
    private final CircuitBreaker circuitBreaker;

    public ProductClientImpl(ProductFeignClient feignClient, CircuitBreakerRegistry registry) {
        this.feignClient = feignClient;
        this.circuitBreaker = registry.circuitBreaker("product-service");
    }

    @Override
    public Optional<ProductInfo> findById(Long productId) {
        return ResilientCall.findOptional(circuitBreaker, () -> feignClient.findById(productId))
                .map(ProductClientImpl::toInfo);
    }

    private static ProductInfo toInfo(ProductClientResponseDTO dto) {
        return new ProductInfo(dto.id(), dto.name(), dto.price(), dto.stock());
    }
}
