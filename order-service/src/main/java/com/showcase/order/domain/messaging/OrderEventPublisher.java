package com.showcase.order.domain.messaging;

import com.showcase.order.domain.event.OrderCancelledEvent;
import com.showcase.order.domain.event.OrderCreatedEvent;

/**
 * Publishing of order events (outbound port). It is called within the transaction of the business change:
 * the event is stored with it and sent later.
 */
public interface OrderEventPublisher {

    void publish(OrderCreatedEvent event);

    void publish(OrderCancelledEvent event);
}
