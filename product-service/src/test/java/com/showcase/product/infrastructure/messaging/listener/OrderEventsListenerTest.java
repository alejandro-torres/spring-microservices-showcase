package com.showcase.product.infrastructure.messaging.listener;

import static org.mockito.Mockito.verify;

import com.showcase.events.EventEnvelopeDTO;
import com.showcase.events.order.OrderCancelledMessageDTO;
import com.showcase.events.order.OrderCreatedMessageDTO;
import com.showcase.events.order.OrderLineMessageDTO;
import com.showcase.product.application.service.StockLine;
import com.showcase.product.application.service.StockService;
import com.showcase.product.infrastructure.messaging.mapper.StockMessageMapperImpl;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderEventsListenerTest {

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final List<OrderLineMessageDTO> LINES = List.of(new OrderLineMessageDTO(10L, 2));

    @Mock
    private StockService stockService;

    private OrderEventsListener listener;

    @BeforeEach
    void setUp() {
        listener = new OrderEventsListener(stockService, new StockMessageMapperImpl());
    }

    @Nested
    @DisplayName("mensajes de order-service")
    class Messages {

        @Test
        @DisplayName("order.created reserva el stock de sus líneas")
        void onOrderCreated_shouldReserve() {
            listener.onOrderCreated(envelope("order.created", new OrderCreatedMessageDTO(7L, 3L, LINES)));

            verify(stockService).reserve(EVENT_ID, 7L, List.of(new StockLine(10L, 2)));
        }

        @Test
        @DisplayName("order.cancelled devuelve el stock de sus líneas")
        void onOrderCancelled_shouldRelease() {
            listener.onOrderCancelled(envelope("order.cancelled", new OrderCancelledMessageDTO(7L, LINES)));

            verify(stockService).release(EVENT_ID, 7L, List.of(new StockLine(10L, 2)));
        }
    }

    private static <T> EventEnvelopeDTO<T> envelope(String type, T payload) {
        return new EventEnvelopeDTO<>(EVENT_ID, type, 1, Instant.now(), "order-service", payload);
    }
}
