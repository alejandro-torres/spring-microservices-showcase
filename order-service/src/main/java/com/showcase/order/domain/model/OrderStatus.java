package com.showcase.order.domain.model;

/**
 * Ciclo de vida de un pedido. PENDING espera la reserva de stock (saga con product-service), que lo pasa a
 * CONFIRMED o REJECTED. Solo un pedido confirmado (con el stock ya reservado) se puede cancelar; mientras está
 * PENDING la saga sigue en curso.
 */
public enum OrderStatus {

    PENDING,
    CONFIRMED,
    REJECTED,
    CANCELLED;

    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case PENDING -> target == CONFIRMED || target == REJECTED;
            case CONFIRMED -> target == CANCELLED;
            case REJECTED, CANCELLED -> false;
        };
    }

    /** Estados finales: el pedido ya no cambia y se puede borrar. */
    public boolean isFinal() {
        return this == REJECTED || this == CANCELLED;
    }
}
