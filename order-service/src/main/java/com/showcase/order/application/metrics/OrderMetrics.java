package com.showcase.order.application.metrics;

/**
 * Business metrics of orders (outbound port). The {@code *ServiceImpl} classes only know this interface; the
 * Micrometer implementation is in the infrastructure and only counts what actually commits.
 */
public interface OrderMetrics {

    void orderCreated();

    void orderConfirmed();

    /** @param reason rejection reason given by product-service (INSUFFICIENT_STOCK, PRODUCT_NOT_FOUND) */
    void orderRejected(String reason);

    void orderCancelled();
}
