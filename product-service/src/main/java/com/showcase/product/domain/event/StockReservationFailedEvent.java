package com.showcase.product.domain.event;

import java.time.Instant;

/**
 * The order's stock could not be reserved and nothing was modified. {@code reason} is the error code
 * ({@code INSUFFICIENT_STOCK}, {@code PRODUCT_NOT_FOUND}) and {@code productId} the product that failed.
 */
public record StockReservationFailedEvent(Long orderId, String reason, Long productId, Instant occurredAt) {
}
