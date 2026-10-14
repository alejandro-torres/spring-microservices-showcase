package com.showcase.product.domain.event;

import java.time.Instant;

/**
 * No se ha podido reservar el stock del pedido y no se ha modificado nada. {@code reason} es el código del
 * error ({@code INSUFFICIENT_STOCK}, {@code PRODUCT_NOT_FOUND}) y {@code productId} el producto que falló.
 */
public record StockReservationFailedEvent(Long orderId, String reason, Long productId, Instant occurredAt) {
}
