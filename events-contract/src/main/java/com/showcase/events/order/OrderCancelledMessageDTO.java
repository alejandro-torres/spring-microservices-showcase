package com.showcase.events.order;

import java.util.List;

/** Confirmed order being cancelled: product-service returns the units of its lines to the stock. */
public record OrderCancelledMessageDTO(Long orderId, List<OrderLineMessageDTO> lines) {
}
