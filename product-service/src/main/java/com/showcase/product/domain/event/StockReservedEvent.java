package com.showcase.product.domain.event;

import java.time.Instant;

/** The stock of all the order's lines has been reserved. */
public record StockReservedEvent(Long orderId, Instant occurredAt) {
}
