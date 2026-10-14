package com.showcase.events.order;

import java.util.List;

/** Pedido nuevo en estado PENDING: product-service intenta reservar el stock de todas sus líneas. */
public record OrderCreatedMessageDTO(Long orderId, Long customerId, List<OrderLineMessageDTO> lines) {
}
