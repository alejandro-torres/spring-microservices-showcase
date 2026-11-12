package com.showcase.order.application.service;

import java.util.UUID;

/**
 * product-service's responses in the order creation saga (inbound port of the listeners).
 * {@code eventId} makes each operation idempotent: a repeated message has no effect.
 */
public interface OrderSagaService {

    void confirm(UUID eventId, Long orderId);

    void reject(UUID eventId, Long orderId, String reason);
}
