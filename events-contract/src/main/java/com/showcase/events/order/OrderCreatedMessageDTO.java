package com.showcase.events.order;

import java.util.List;

/** New order in PENDING status: product-service tries to reserve the stock of all its lines. */
public record OrderCreatedMessageDTO(Long orderId, Long customerId, List<OrderLineMessageDTO> lines) {
}
