package com.showcase.events.product;

/**
 * No se ha reservado nada. {@code reason} es un código estable ({@code INSUFFICIENT_STOCK},
 * {@code PRODUCT_NOT_FOUND}) y {@code productId} el primer producto que ha fallado.
 */
public record StockReservationFailedMessageDTO(Long orderId, String reason, Long productId) {
}
