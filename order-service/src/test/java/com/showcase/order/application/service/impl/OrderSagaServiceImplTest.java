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
        @DisplayName("registra el evento y pasa el pedido pendiente a CONFIRMED")
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
        @DisplayName("ignora un evento duplicado sin tocar el pedido")
        void confirm_duplicate_shouldDoNothing() {
            // given
            given(processedEvents.registerIfAbsent(EVENT_ID, "stock.reserved")).willReturn(false);

            // when
            sagaService.confirm(EVENT_ID, 1L);

            // then
            verifyNoInteractions(orderRepository, metrics);
        }

        @Test
        @DisplayName("lanza ORDER_NOT_FOUND si el pedido no existe (el mensaje irá a la DLQ)")
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
        @DisplayName("lanza INVALID_STATUS_TRANSITION si el pedido ya no está pendiente y no guarda nada")
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
        @DisplayName("pasa el pedido a REJECTED con el motivo de product-service")
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
        @DisplayName("ignora un evento duplicado")
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
