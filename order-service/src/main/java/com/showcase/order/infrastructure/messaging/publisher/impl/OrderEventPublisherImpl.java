package com.showcase.order.infrastructure.messaging.publisher.impl;

import com.showcase.common.infrastructure.messaging.outbox.OutboxEventWriter;
import com.showcase.events.EventTypes;
import com.showcase.order.domain.event.OrderCancelledEvent;
import com.showcase.order.domain.event.OrderCreatedEvent;
import com.showcase.order.domain.messaging.OrderEventPublisher;
import com.showcase.order.infrastructure.messaging.mapper.OrderMessageMapper;
import org.springframework.stereotype.Component;

/**
 * No envía nada a RabbitMQ: guarda el mensaje en el outbox dentro de la transacción en curso
 * ({@code Propagation.MANDATORY}). El envío real lo hace el {@code OutboxPublisher} cuando la transacción se
 * ha confirmado.
 */
@Component
public class OrderEventPublisherImpl implements OrderEventPublisher {

    static final String AGGREGATE_TYPE = "Order";

    private final OutboxEventWriter outbox;
    private final OrderMessageMapper mapper;

    public OrderEventPublisherImpl(OutboxEventWriter outbox, OrderMessageMapper mapper) {
        this.outbox = outbox;
        this.mapper = mapper;
    }

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
