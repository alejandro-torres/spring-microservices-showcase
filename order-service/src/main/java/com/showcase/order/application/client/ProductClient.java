package com.showcase.order.application.client;

import java.util.Optional;

/**
 * Product lookup in product-service. The implementation decides the transport (Feign) and the resilience
 * (circuit breaker); if the service does not respond it throws ExternalServiceException (503).
 */
public interface ProductClient {

    /** Empty if the product does not exist or has been deleted. */
    Optional<ProductInfo> findById(Long productId);
}
