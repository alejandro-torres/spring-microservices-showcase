package com.showcase.product.infrastructure.messaging.listener;

import com.showcase.common.infrastructure.messaging.error.LoggingRabbitListenerErrorHandler;
import com.showcase.events.EventEnvelopeDTO;
import com.showcase.events.order.OrderCancelledMessageDTO;
import com.showcase.events.order.OrderCreatedMessageDTO;
import com.showcase.product.application.service.StockService;
import com.showcase.product.infrastructure.messaging.mapper.StockMessageMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Adaptador de entrada de los eventos de order-service: convierte el mensaje y llama a {@link StockService}, sin
 * lógica de negocio. Todas las réplicas escuchan la misma cola (competing consumers): cada mensaje lo procesa
 * una sola.
 */
@Component
public class OrderEventsListener {

    private final StockService stockService;
    private final StockMessageMapper mapper;

    public OrderEventsListener(StockService stockService, StockMessageMapper mapper) {
        this.stockService = stockService;
        this.mapper = mapper;
    }

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
