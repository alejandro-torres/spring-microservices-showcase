package com.showcase.order.domain.event;

/** Producto y unidades de una línea, lo único que necesita saber product-service para reservar stock. */
public record OrderEventLine(Long productId, int quantity) {
}
