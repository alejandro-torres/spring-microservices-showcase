package com.showcase.order.infrastructure.messaging.listener;

import static org.mockito.Mockito.verify;

import com.showcase.events.EventEnvelopeDTO;
import com.showcase.events.product.StockReservationFailedMessageDTO;
import com.showcase.events.product.StockReservedMessageDTO;
import com.showcase.order.application.service.OrderSagaService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StockEventsListenerTest {

    private static final UUID EVENT_ID = UUID.randomUUID();

    @Mock
    private OrderSagaService sagaService;

    @InjectMocks
    private StockEventsListener listener;

    @Nested
    @DisplayName("mensajes de product-service")
    class Messages {

        @Test
        @DisplayName("stock.reserved confirma el pedido con el eventId del sobre")
        void onStockReserved_shouldConfirm() {
            // when
            listener.onStockReserved(envelope("stock.reserved", new StockReservedMessageDTO(7L)));

            // then
            verify(sagaService).confirm(EVENT_ID, 7L);
        }

        @Test
        @DisplayName("stock.reservation-failed rechaza el pedido con el motivo")
        void onStockReservationFailed_shouldReject() {
            // when
            listener.onStockReservationFailed(envelope("stock.reservation-failed",
                    new StockReservationFailedMessageDTO(7L, "INSUFFICIENT_STOCK", 10L)));

            // then
            verify(sagaService).reject(EVENT_ID, 7L, "INSUFFICIENT_STOCK");
        }
    }

    private static <T> EventEnvelopeDTO<T> envelope(String type, T payload) {
        return new EventEnvelopeDTO<>(EVENT_ID, type, 1, Instant.now(), "product-service", payload);
    }
}
