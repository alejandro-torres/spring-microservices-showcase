package com.showcase.product.domain.event;

import java.time.Instant;

/** Se ha reservado el stock de todas las líneas del pedido. */
public record StockReservedEvent(Long orderId, Instant occurredAt) {
}
