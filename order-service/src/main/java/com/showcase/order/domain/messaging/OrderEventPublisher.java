package com.showcase.order.domain.messaging;

import com.showcase.order.domain.event.OrderCancelledEvent;
import com.showcase.order.domain.event.OrderCreatedEvent;

/**
 * Publicación de los eventos de pedidos (puerto de salida). Se llama dentro de la transacción del cambio de
 * negocio: el evento se guarda con él y se envía después.
 */
public interface OrderEventPublisher {

    void publish(OrderCreatedEvent event);

    void publish(OrderCancelledEvent event);
}
