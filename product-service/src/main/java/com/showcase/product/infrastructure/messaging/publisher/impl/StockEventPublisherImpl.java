package com.showcase.product.infrastructure.messaging.publisher.impl;

import com.showcase.common.infrastructure.messaging.outbox.OutboxEventWriter;
import com.showcase.events.EventTypes;
import com.showcase.product.domain.event.StockReleasedEvent;
import com.showcase.product.domain.event.StockReservationFailedEvent;
import com.showcase.product.domain.event.StockReservedEvent;
import com.showcase.product.domain.messaging.StockEventPublisher;
import com.showcase.product.infrastructure.messaging.mapper.StockMessageMapper;
import org.springframework.stereotype.Component;

/**
 * Guarda las respuestas de la saga en el outbox dentro de la transacción en curso; el {@code OutboxPublisher}
 * las envía después. El agregado es el pedido, porque las tres respuestas hablan de él.
 */
@Component
public class StockEventPublisherImpl implements StockEventPublisher {

    static final String AGGREGATE_TYPE = "OrderStock";

    private final OutboxEventWriter outbox;
    private final StockMessageMapper mapper;

    public StockEventPublisherImpl(OutboxEventWriter outbox, StockMessageMapper mapper) {
        this.outbox = outbox;
        this.mapper = mapper;
    }

    @Override
    public void publish(StockReservedEvent event) {
        outbox.append(AGGREGATE_TYPE, event.orderId(), EventTypes.STOCK_RESERVED, mapper.toMessage(event),
                event.occurredAt());
    }

    @Override
    public void publish(StockReservationFailedEvent event) {
        outbox.append(AGGREGATE_TYPE, event.orderId(), EventTypes.STOCK_RESERVATION_FAILED,
                mapper.toMessage(event), event.occurredAt());
    }

    @Override
    public void publish(StockReleasedEvent event) {
        outbox.append(AGGREGATE_TYPE, event.orderId(), EventTypes.STOCK_RELEASED, mapper.toMessage(event),
                event.occurredAt());
    }
}
