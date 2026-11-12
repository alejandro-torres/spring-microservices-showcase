package com.showcase.order.application.service.impl;

import static com.showcase.order.testdata.OrderMother.order;
import static com.showcase.order.testdata.OrderMother.pending;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.showcase.common.domain.exception.BusinessRuleException;
import com.showcase.common.domain.exception.ResourceNotFoundException;
import com.showcase.common.domain.messaging.ProcessedEventRegistry;
import com.showcase.order.application.metrics.OrderMetrics;
import com.showcase.order.domain.exception.OrderErrorCode;
import com.showcase.order.domain.model.Order;
import com.showcase.order.domain.model.OrderStatus;
import com.showcase.order.domain.repository.OrderRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderSagaServiceImplTest {

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProcessedEventRegistry processedEvents;

    @Mock
    private OrderMetrics metrics;

    @InjectMocks
    private OrderSagaServiceImpl sagaService;

    @Nested
    @DisplayName("confirm")
    class Confirm {

        @Test
        @DisplayName("records the event and moves the pending order to CONFIRMED")
        void confirm_pending_shouldConfirm() {
            // given
            given(processedEvents.registerIfAbsent(EVENT_ID, "stock.reserved")).willReturn(true);
            given(orderRepository.findById(1L)).willReturn(Optional.of(pending()));

            // when
            sagaService.confirm(EVENT_ID, 1L);

            // then
            var captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            verify(metrics).orderConfirmed();
        }

        @Test
        @DisplayName("ignores a duplicate event without touching the order")
        void confirm_duplicate_shouldDoNothing() {
            // given
            given(processedEvents.registerIfAbsent(EVENT_ID, "stock.reserved")).willReturn(false);

            // when
            sagaService.confirm(EVENT_ID, 1L);

            // then
            verifyNoInteractions(orderRepository, metrics);
        }

        @Test
        @DisplayName("throws ORDER_NOT_FOUND if the order does not exist (the message will go to the DLQ)")
        void confirm_unknownOrder_shouldThrow() {
            // given
            given(processedEvents.registerIfAbsent(EVENT_ID, "stock.reserved")).willReturn(true);
            given(orderRepository.findById(1L)).willReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> sagaService.confirm(EVENT_ID, 1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_NOT_FOUND);
        }

        @Test
        @DisplayName("throws INVALID_STATUS_TRANSITION if the order is no longer pending and saves nothing")
        void confirm_rejectedOrder_shouldThrow() {
            // given
            given(processedEvents.registerIfAbsent(EVENT_ID, "stock.reserved")).willReturn(true);
            given(orderRepository.findById(1L)).willReturn(Optional.of(order(OrderStatus.REJECTED)));

            // when / then
            assertThatThrownBy(() -> sagaService.confirm(EVENT_ID, 1L))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.INVALID_STATUS_TRANSITION);
            verify(orderRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("reject")
    class Reject {

        @Test
        @DisplayName("moves the order to REJECTED with product-service's reason")
        void reject_pending_shouldRejectWithReason() {
            // given
            given(processedEvents.registerIfAbsent(EVENT_ID, "stock.reservation-failed")).willReturn(true);
            given(orderRepository.findById(1L)).willReturn(Optional.of(pending()));

            // when
            sagaService.reject(EVENT_ID, 1L, "INSUFFICIENT_STOCK");

            // then
            var captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.REJECTED);
            assertThat(captor.getValue().getRejectionReason()).isEqualTo("INSUFFICIENT_STOCK");
            verify(metrics).orderRejected("INSUFFICIENT_STOCK");
        }

        @Test
        @DisplayName("ignores a duplicate event")
        void reject_duplicate_shouldDoNothing() {
            // given
            given(processedEvents.registerIfAbsent(EVENT_ID, "stock.reservation-failed")).willReturn(false);

            // when
            sagaService.reject(EVENT_ID, 1L, "INSUFFICIENT_STOCK");

            // then
            verifyNoInteractions(orderRepository, metrics);
        }
    }
}
