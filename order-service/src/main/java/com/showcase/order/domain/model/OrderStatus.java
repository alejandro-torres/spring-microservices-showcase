package com.showcase.order.domain.model;

/**
 * Lifecycle of an order. PENDING awaits the stock reservation (saga with product-service), which moves it to
 * CONFIRMED or REJECTED. Only a confirmed order (with its stock already reserved) can be cancelled; while it is
 * PENDING the saga is still running.
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

    /** Final statuses: the order no longer changes and can be deleted. */
    public boolean isFinal() {
        return this == REJECTED || this == CANCELLED;
    }
}
