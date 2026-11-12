package com.showcase.order.application.service;

import com.showcase.order.domain.model.Order;

/**
 * Transactional part of creating an order, separate from {@link OrderService} so that calls to other
 * services happen before opening the transaction (see {@code OrderServiceImpl}).
 */
public interface OrderWriter {

    /** Saves the order and its {@code OrderCreatedEvent} in the same transaction. */
    Order create(Order order);
}
