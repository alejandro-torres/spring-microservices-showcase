package com.showcase.product.application.service;

import java.util.List;
import java.util.UUID;

/**
 * Reserva y devolución de stock para la saga de pedidos (puerto de entrada de los listeners). {@code eventId}
 * hace idempotente cada operación: un mensaje repetido no vuelve a tocar el stock.
 */
public interface StockService {

    /** Reserva todas las líneas o ninguna, y publica el resultado (reservado o fallido). */
    void reserve(UUID eventId, Long orderId, List<StockLine> lines);

    /** Devuelve al stock las unidades de un pedido cancelado (compensación). */
    void release(UUID eventId, Long orderId, List<StockLine> lines);
}
