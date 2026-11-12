package com.showcase.order.domain.event;

/** Product and units of a line, all product-service needs to know to reserve stock. */
public record OrderEventLine(Long productId, int quantity) {
}
