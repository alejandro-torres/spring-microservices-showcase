package com.showcase.product.application.metrics;

/**
 * Métricas de negocio del stock (puerto de salida); la implementación con Micrometer está en la infraestructura.
 */
public interface StockMetrics {

    void stockReserved();

    /** @param reason INSUFFICIENT_STOCK o PRODUCT_NOT_FOUND */
    void reservationFailed(String reason);

    void stockReleased();
}
