package com.showcase.product.infrastructure.messaging.listener;

import com.showcase.common.infrastructure.messaging.error.LoggingRabbitListenerErrorHandler;
import com.showcase.events.EventEnvelopeDTO;
import com.showcase.events.order.OrderCancelledMessageDTO;
import com.showcase.events.order.OrderCreatedMessageDTO;
import com.showcase.product.application.service.StockService;
import com.showcase.product.infrastructure.messaging.mapper.StockMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Inbound adapter for order-service events: it converts the message and calls {@link StockService}, with no
 * business logic. All replicas listen to the same queue (competing consumers): each message is processed by
 * only one.
 */
@Component
@RequiredArgsConstructor
public class OrderEventsListener {

    private final StockService stockService;
    private final StockMessageMapper mapper;

    @RabbitListener(queues = "${showcase.messaging.consumers.order-created.queue}",
            errorHandler = LoggingRabbitListenerErrorHandler.BEAN_NAME)
    public void onOrderCreated(EventEnvelopeDTO<OrderCreatedMessageDTO> envelope) {
        OrderCreatedMessageDTO payload = envelope.payload();
        stockService.reserve(envelope.eventId(), payload.orderId(), mapper.toStockLines(payload.lines()));
    }

    @RabbitListener(queues = "${showcase.messaging.consumers.order-cancelled.queue}",
            errorHandler = LoggingRabbitListenerErrorHandler.BEAN_NAME)
    public void onOrderCancelled(EventEnvelopeDTO<OrderCancelledMessageDTO> envelope) {
        OrderCancelledMessageDTO payload = envelope.payload();
        stockService.release(envelope.eventId(), payload.orderId(), mapper.toStockLines(payload.lines()));
    }
}
