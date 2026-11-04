package com.showcase.product.application.metrics;

/**
 * Business metrics of stock (outbound port); the Micrometer implementation is in the infrastructure.
 */
public interface StockMetrics {

    void stockReserved();

    /** @param reason INSUFFICIENT_STOCK or PRODUCT_NOT_FOUND */
    void reservationFailed(String reason);

    void stockReleased();
}
