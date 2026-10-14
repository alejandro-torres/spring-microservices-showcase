package com.showcase.order.domain.event;

import com.showcase.order.domain.model.Order;
import java.time.Instant;
import java.util.List;

/** Se ha creado un pedido PENDING: empieza la saga de reserva de stock. */
public record OrderCreatedEvent(Long orderId, Long customerId, List<OrderEventLine> lines, Instant occurredAt) {

    public OrderCreatedEvent {
        lines = List.copyOf(lines);
    }

    public static OrderCreatedEvent of(Order order, Instant occurredAt) {
        return new OrderCreatedEvent(order.getId(), order.getCustomerId(), linesOf(order), occurredAt);
    }

    static List<OrderEventLine> linesOf(Order order) {
        return order.getItems().stream()
                .map(item -> new OrderEventLine(item.productId(), item.quantity()))
                .toList();
    }
}
