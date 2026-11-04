package com.showcase.events.product;

/**
 * Nothing was reserved. {@code reason} is a stable code ({@code INSUFFICIENT_STOCK},
 * {@code PRODUCT_NOT_FOUND}) and {@code productId} the first product that failed.
 */
public record StockReservationFailedMessageDTO(Long orderId, String reason, Long productId) {
}
