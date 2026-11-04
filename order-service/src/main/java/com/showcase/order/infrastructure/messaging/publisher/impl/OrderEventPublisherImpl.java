package com.showcase.order.infrastructure.messaging.publisher.impl;

import com.showcase.common.infrastructure.messaging.outbox.OutboxEventWriter;
import com.showcase.events.EventTypes;
import com.showcase.order.domain.event.OrderCancelledEvent;
import com.showcase.order.domain.event.OrderCreatedEvent;
import com.showcase.order.domain.messaging.OrderEventPublisher;
import com.showcase.order.infrastructure.messaging.mapper.OrderMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Sends nothing to RabbitMQ: it stores the message in the outbox within the current transaction
 * ({@code Propagation.MANDATORY}). The actual send is done by the {@code OutboxPublisher} once the transaction
 * has committed.
 */
@Component
@RequiredArgsConstructor
public class OrderEventPublisherImpl implements OrderEventPublisher {

    static final String AGGREGATE_TYPE = "Order";

    private final OutboxEventWriter outbox;
    private final OrderMessageMapper mapper;

    @Override
    public void publish(OrderCreatedEvent event) {
        outbox.append(AGGREGATE_TYPE, event.orderId(), EventTypes.ORDER_CREATED, mapper.toMessage(event),
                event.occurredAt());
    }

    @Override
    public void publish(OrderCancelledEvent event) {
        outbox.append(AGGREGATE_TYPE, event.orderId(), EventTypes.ORDER_CANCELLED, mapper.toMessage(event),
                event.occurredAt());
    }
}
