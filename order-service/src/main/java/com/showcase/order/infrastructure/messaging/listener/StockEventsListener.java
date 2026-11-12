package com.showcase.order.infrastructure.messaging.listener;

import com.showcase.common.infrastructure.messaging.error.LoggingRabbitListenerErrorHandler;
import com.showcase.events.EventEnvelopeDTO;
import com.showcase.events.product.StockReservationFailedMessageDTO;
import com.showcase.events.product.StockReservedMessageDTO;
import com.showcase.order.application.service.OrderSagaService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Inbound adapter for product-service's responses in the saga. Like a controller: it converts the message and
 * calls the service interface, with no business logic or transaction of its own. If the service throws an
 * exception, Spring AMQP retries or sends the message to the DLQ; the ACK only happens if everything went well.
 */
@Component
@RequiredArgsConstructor
public class StockEventsListener {

    private final OrderSagaService sagaService;

    @RabbitListener(queues = "${showcase.messaging.consumers.stock-reserved.queue}",
            errorHandler = LoggingRabbitListenerErrorHandler.BEAN_NAME)
    public void onStockReserved(EventEnvelopeDTO<StockReservedMessageDTO> envelope) {
        sagaService.confirm(envelope.eventId(), envelope.payload().orderId());
    }

    @RabbitListener(queues = "${showcase.messaging.consumers.stock-reservation-failed.queue}",
            errorHandler = LoggingRabbitListenerErrorHandler.BEAN_NAME)
    public void onStockReservationFailed(EventEnvelopeDTO<StockReservationFailedMessageDTO> envelope) {
        StockReservationFailedMessageDTO payload = envelope.payload();
        sagaService.reject(envelope.eventId(), payload.orderId(), payload.reason());
    }
}
