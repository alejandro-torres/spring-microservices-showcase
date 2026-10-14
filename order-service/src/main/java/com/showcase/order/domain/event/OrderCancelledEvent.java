package com.showcase.order.domain.event;

import com.showcase.order.domain.model.Order;
import java.time.Instant;
import java.util.List;

/** Se ha cancelado un pedido confirmado: hay que devolver su stock (compensación de la saga). */
public record OrderCancelledEvent(Long orderId, List<OrderEventLine> lines, Instant occurredAt) {

    public OrderCancelledEvent {
        lines = List.copyOf(lines);
    }

    public static OrderCancelledEvent of(Order order, Instant occurredAt) {
        return new OrderCancelledEvent(order.getId(), OrderCreatedEvent.linesOf(order), occurredAt);
    }
}
