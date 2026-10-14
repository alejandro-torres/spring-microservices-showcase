package com.showcase.order.application.metrics;

/**
 * Métricas de negocio de los pedidos (puerto de salida). Los {@code *ServiceImpl} solo conocen esta interfaz; la
 * implementación con Micrometer está en la infraestructura y solo cuenta lo que llega a confirmarse.
 */
public interface OrderMetrics {

    void orderCreated();

    void orderConfirmed();

    /** @param reason motivo del rechazo que da product-service (INSUFFICIENT_STOCK, PRODUCT_NOT_FOUND) */
    void orderRejected(String reason);

    void orderCancelled();
}
