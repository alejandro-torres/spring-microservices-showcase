package com.showcase.order.application.service;

import java.util.UUID;

/**
 * Respuestas de product-service en la saga de creación de pedidos (puerto de entrada de los listeners).
 * {@code eventId} hace idempotente cada operación: un mensaje repetido no tiene efecto.
 */
public interface OrderSagaService {

    void confirm(UUID eventId, Long orderId);

    void reject(UUID eventId, Long orderId, String reason);
}
