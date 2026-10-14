package com.showcase.order.application.service.impl;

import static com.showcase.order.testdata.OrderMother.item;
import static com.showcase.order.testdata.OrderMother.pending;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.order.domain.exception.OrderErrorCode;
import com.showcase.order.domain.model.Order;
import com.showcase.order.domain.repository.OrderRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderWriterImplTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderWriterImpl orderWriter;

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("guarda el pedido")
        void create_shouldSave() {
            var order = pending();
            given(orderRepository.save(order)).willReturn(order);

            assertThat(orderWriter.create(order)).isSameAs(order);
        }
    }

    @Nested
    @DisplayName("replaceItems")
    class ReplaceItems {

        @Test
        @DisplayName("vuelve a leer el pedido, comprueba la versión y guarda las líneas nuevas")
        void replaceItems_shouldSave() {
            given(orderRepository.findById(1L)).willReturn(Optional.of(pending()));
            given(orderRepository.save(any(Order.class))).willAnswer(invocation -> invocation.getArgument(0));

            var order = orderWriter.replaceItems(1L, 0L, List.of(item(12, "3", 1)));

            assertThat(order.getTotal()).isEqualByComparingTo("3.00");
        }

        @Test
        @DisplayName("lanza CONCURRENT_MODIFICATION si el pedido cambió desde que se leyó")
        void replaceItems_withStaleVersion_shouldThrow() {
            given(orderRepository.findById(1L)).willReturn(Optional.of(pending()));

            assertThatThrownBy(() -> orderWriter.replaceItems(1L, 5L, List.of(item(12, "3", 1))))
                    .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.CONCURRENT_MODIFICATION);
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("lanza ORDER_NOT_FOUND si el pedido se borró entretanto")
        void replaceItems_missing_shouldThrow() {
            assertThatThrownBy(() -> orderWriter.replaceItems(1L, 0L, List.of(item(12, "3", 1))))
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_NOT_FOUND);
        }
    }
}
