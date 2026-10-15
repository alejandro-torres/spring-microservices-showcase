package com.showcase.order.application.service.impl;

import static com.showcase.order.testdata.OrderMother.pending;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import com.showcase.order.application.metrics.OrderMetrics;
import com.showcase.order.domain.event.OrderCreatedEvent;
import com.showcase.order.domain.event.OrderEventLine;
import com.showcase.order.domain.messaging.OrderEventPublisher;
import com.showcase.order.domain.model.Order;
import com.showcase.order.domain.repository.OrderRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderWriterImplTest {

    private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventPublisher eventPublisher;

    @Mock
    private OrderMetrics metrics;

    private OrderWriterImpl orderWriter;

    @BeforeEach
    void setUp() {
        orderWriter = new OrderWriterImpl(orderRepository, eventPublisher, metrics, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("guarda el pedido y después publica OrderCreatedEvent con sus líneas")
        void create_shouldSaveAndPublishEvent() {
            // given
            var order = pending();
            given(orderRepository.save(order)).willReturn(order);

            // when
            var saved = orderWriter.create(order);

            // then
            assertThat(saved).isSameAs(order);
            var captor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
            var ordered = inOrder(orderRepository, eventPublisher);
            ordered.verify(orderRepository).save(order);
            ordered.verify(eventPublisher).publish(captor.capture());
            assertThat(captor.getValue()).isEqualTo(new OrderCreatedEvent(1L, 3L,
                    List.of(new OrderEventLine(10L, 2), new OrderEventLine(11L, 1)), NOW));
            verify(metrics).orderCreated();
        }

        @Test
        @DisplayName("el evento lleva el id que asigna la base de datos, no el del pedido sin guardar")
        void create_shouldPublishSavedId() {
            // given
            var unsaved = Order.create(3L, pending().getShippingAddress(),
                    pending().getItems());
            given(orderRepository.save(any())).willReturn(pending());

            // when
            orderWriter.create(unsaved);

            // then
            var captor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
            verify(eventPublisher).publish(captor.capture());
            assertThat(captor.getValue().orderId()).isEqualTo(1L);
        }
    }
}
