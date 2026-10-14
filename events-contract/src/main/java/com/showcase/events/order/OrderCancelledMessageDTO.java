package com.showcase.events.order;

import java.util.List;

/** Pedido confirmado que se cancela: product-service devuelve al stock las unidades de sus líneas. */
public record OrderCancelledMessageDTO(Long orderId, List<OrderLineMessageDTO> lines) {
}
