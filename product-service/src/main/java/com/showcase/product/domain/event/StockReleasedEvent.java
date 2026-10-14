package com.showcase.product.domain.event;

import java.time.Instant;

/** Se ha devuelto al stock lo que tenía reservado un pedido cancelado. */
public record StockReleasedEvent(Long orderId, Instant occurredAt) {
}
