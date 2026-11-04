package com.showcase.order.domain.event;

import com.showcase.order.domain.model.Order;
import java.time.Instant;
import java.util.List;

/** A confirmed order has been cancelled: its stock must be returned (saga compensation). */
public record OrderCancelledEvent(Long orderId, List<OrderEventLine> lines, Instant occurredAt) {

    public OrderCancelledEvent {
        lines = List.copyOf(lines);
    }

    public static OrderCancelledEvent of(Order order, Instant occurredAt) {
        return new OrderCancelledEvent(order.getId(), OrderCreatedEvent.linesOf(order), occurredAt);
    }
}
