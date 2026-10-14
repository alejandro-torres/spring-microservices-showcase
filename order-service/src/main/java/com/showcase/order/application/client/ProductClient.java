package com.showcase.order.application.client;

import java.util.Optional;

/**
 * Consulta de productos en product-service. La implementación decide el transporte (Feign) y la resiliencia
 * (circuit breaker); si el servicio no responde lanza ExternalServiceException (503).
 */
public interface ProductClient {

    /** Vacío si el producto no existe o está borrado. */
    Optional<ProductInfo> findById(Long productId);
}
