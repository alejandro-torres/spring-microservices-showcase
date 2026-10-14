package com.showcase.product.infrastructure.messaging.publisher.impl;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.showcase.common.infrastructure.messaging.outbox.OutboxEventWriter;
import com.showcase.events.product.StockReleasedMessageDTO;
import com.showcase.events.product.StockReservationFailedMessageDTO;
import com.showcase.events.product.StockReservedMessageDTO;
import com.showcase.product.domain.event.StockReleasedEvent;
import com.showcase.product.domain.event.StockReservationFailedEvent;
import com.showcase.product.domain.event.StockReservedEvent;
import com.showcase.product.infrastructure.messaging.mapper.StockMessageMapperImpl;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StockEventPublisherImplTest {

    private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");

    @Mock
    private OutboxEventWriter outbox;

    private StockEventPublisherImpl publisher;

    @BeforeEach
    void setUp() {
        publisher = new StockEventPublisherImpl(outbox, new StockMessageMapperImpl());
    }

    @Nested
    @DisplayName("publish")
    class Publish {

        @Test
        @DisplayName("guarda stock.reserved en el outbox")
        void publish_reserved_shouldAppend() {
            publisher.publish(new StockReservedEvent(7L, NOW));

            verify(outbox).append(eq("OrderStock"), eq(7L), eq("stock.reserved"),
                    eq(new StockReservedMessageDTO(7L)), eq(NOW));
        }

        @Test
        @DisplayName("guarda stock.reservation-failed con el motivo y el producto")
        void publish_failed_shouldAppend() {
            publisher.publish(new StockReservationFailedEvent(7L, "INSUFFICIENT_STOCK", 10L, NOW));

            verify(outbox).append(eq("OrderStock"), eq(7L), eq("stock.reservation-failed"),
                    eq(new StockReservationFailedMessageDTO(7L, "INSUFFICIENT_STOCK", 10L)), eq(NOW));
        }

        @Test
        @DisplayName("guarda stock.released en el outbox")
        void publish_released_shouldAppend() {
            publisher.publish(new StockReleasedEvent(7L, NOW));

            verify(outbox).append(eq("OrderStock"), eq(7L), eq("stock.released"),
                    eq(new StockReleasedMessageDTO(7L)), eq(NOW));
        }
    }
}
