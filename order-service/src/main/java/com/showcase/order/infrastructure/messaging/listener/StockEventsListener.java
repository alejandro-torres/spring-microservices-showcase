package com.showcase.order.infrastructure.messaging.listener;

import com.showcase.common.infrastructure.messaging.error.LoggingRabbitListenerErrorHandler;
import com.showcase.events.EventEnvelopeDTO;
import com.showcase.events.product.StockReservationFailedMessageDTO;
import com.showcase.events.product.StockReservedMessageDTO;
import com.showcase.order.application.service.OrderSagaService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Adaptador de entrada de las respuestas de product-service en la saga. Igual que un controller: convierte el
 * mensaje y llama a la interfaz de servicio, sin lógica de negocio ni transacción propia. Si el servicio lanza
 * una excepción, Spring AMQP reintenta o manda el mensaje a la DLQ; el ACK solo se hace si todo ha ido bien.
 */
@Component
public class StockEventsListener {

    private final OrderSagaService sagaService;

    public StockEventsListener(OrderSagaService sagaService) {
        this.sagaService = sagaService;
    }

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
