package com.showcase.order.infrastructure.messaging.publisher.impl;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.showcase.common.infrastructure.messaging.outbox.OutboxEventWriter;
import com.showcase.events.order.OrderCancelledMessageDTO;
import com.showcase.events.order.OrderCreatedMessageDTO;
import com.showcase.events.order.OrderLineMessageDTO;
import com.showcase.order.domain.event.OrderCancelledEvent;
import com.showcase.order.domain.event.OrderCreatedEvent;
import com.showcase.order.domain.event.OrderEventLine;
import com.showcase.order.infrastructure.messaging.mapper.OrderMessageMapperImpl;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderEventPublisherImplTest {

    private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");
    private static final List<OrderEventLine> LINES = List.of(new OrderEventLine(10L, 2));

    @Mock
    private OutboxEventWriter outbox;

    private OrderEventPublisherImpl publisher;

    @BeforeEach
    void setUp() {
        publisher = new OrderEventPublisherImpl(outbox, new OrderMessageMapperImpl());
    }

    @Nested
    @DisplayName("publish")
    class Publish {

        @Test
        @DisplayName("guarda order.created en el outbox con el contrato público del mensaje")
        void publish_orderCreated_shouldAppendToOutbox() {
            // when
            publisher.publish(new OrderCreatedEvent(7L, 3L, LINES, NOW));

            // then
            verify(outbox).append(eq("Order"), eq(7L), eq("order.created"),
                    eq(new OrderCreatedMessageDTO(7L, 3L, List.of(new OrderLineMessageDTO(10L, 2)))), eq(NOW));
        }

        @Test
        @DisplayName("guarda order.cancelled en el outbox con las líneas que hay que devolver")
        void publish_orderCancelled_shouldAppendToOutbox() {
            // when
            publisher.publish(new OrderCancelledEvent(7L, LINES, NOW));

            // then
            verify(outbox).append(eq("Order"), eq(7L), eq("order.cancelled"),
                    eq(new OrderCancelledMessageDTO(7L, List.of(new OrderLineMessageDTO(10L, 2)))), eq(NOW));
        }
    }
}
