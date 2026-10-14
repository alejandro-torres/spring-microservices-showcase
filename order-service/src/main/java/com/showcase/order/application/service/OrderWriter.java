package com.showcase.order.application.service;

import com.showcase.order.domain.model.Order;
import com.showcase.order.domain.model.OrderItem;
import java.util.List;

/**
 * Parte transaccional de crear y modificar pedidos, separada de {@link OrderService} para que las llamadas
 * a otros servicios se hagan antes de abrir la transacción (ver {@code OrderServiceImpl}).
 */
public interface OrderWriter {

    Order create(Order order);

    Order replaceItems(Long id, Long version, List<OrderItem> items);
}
