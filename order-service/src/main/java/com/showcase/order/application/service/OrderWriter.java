package com.showcase.order.application.service;

import com.showcase.order.domain.model.Order;

/**
 * Parte transaccional de crear un pedido, separada de {@link OrderService} para que las llamadas a otros
 * servicios se hagan antes de abrir la transacción (ver {@code OrderServiceImpl}).
 */
public interface OrderWriter {

    /** Guarda el pedido y su {@code OrderCreatedEvent} en la misma transacción. */
    Order create(Order order);
}
