package com.showcase.order.domain.model;

import java.util.Set;

/**
 * Ciclo de vida de un pedido. PENDING espera la reserva de stock (saga con product-service), que lo pasa a
 * CONFIRMED o REJECTED. Un pedido pendiente o confirmado se puede cancelar.
 */
public enum OrderStatus {

    PENDING,
    CONFIRMED,
    REJECTED,
    CANCELLED;

    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case PENDING -> Set.of(CONFIRMED, REJECTED, CANCELLED).contains(target);
            case CONFIRMED -> target == CANCELLED;
            case REJECTED, CANCELLED -> false;
        };
    }

    /** Estados finales: el pedido ya no cambia y se puede borrar. */
    public boolean isFinal() {
        return this == REJECTED || this == CANCELLED;
    }
}
