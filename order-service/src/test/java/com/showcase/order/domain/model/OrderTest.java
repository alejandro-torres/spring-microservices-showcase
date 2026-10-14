package com.showcase.order.domain.model;

import static com.showcase.order.testdata.OrderMother.ADDRESS;
import static com.showcase.order.testdata.OrderMother.item;
import static com.showcase.order.testdata.OrderMother.order;
import static com.showcase.order.testdata.OrderMother.pending;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.showcase.common.domain.exception.BusinessRuleException;
import com.showcase.common.domain.exception.InvalidRequestException;
import com.showcase.order.domain.exception.OrderErrorCode;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OrderTest {

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("crea el pedido pendiente y calcula el total a partir de las líneas")
        void create_shouldBePendingWithTotal() {
            var order = Order.create(3L, ADDRESS, List.of(item(10, "5.5", 2), item(11, "20", 1)));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(order.getTotal()).isEqualByComparingTo("31.00");
            assertThat(order.getItems().getFirst().unitPrice()).isEqualTo(new BigDecimal("5.50"));
            assertThat(order.containsProduct(11L)).isTrue();
        }

        @Test
        @DisplayName("rechaza un pedido sin líneas o con más de 20")
        void create_withInvalidItemCount_shouldThrow() {
            var tooMany = java.util.stream.LongStream.rangeClosed(1, 21).mapToObj(id -> item(id, "1", 1)).toList();

            assertThatThrownBy(() -> Order.create(3L, ADDRESS, List.of()))
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.INVALID_ORDER_DATA);
            assertThatThrownBy(() -> Order.create(3L, ADDRESS, tooMany))
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.INVALID_ORDER_DATA);
            assertThatThrownBy(() -> Order.create(3L, ADDRESS, null))
                    .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        @DisplayName("rechaza un pedido sin cliente o sin dirección")
        void create_withoutCustomer_shouldThrow() {
            assertThatThrownBy(() -> Order.create(null, ADDRESS, List.of(item(1, "1", 1))))
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.INVALID_ORDER_DATA);
            assertThatThrownBy(() -> Order.create(3L, null, List.of(item(1, "1", 1))))
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.INVALID_ORDER_DATA);
        }

        @Test
        @DisplayName("rechaza el mismo producto en dos líneas")
        void create_withDuplicateProduct_shouldThrow() {
            assertThatThrownBy(() -> Order.create(3L, ADDRESS, List.of(item(10, "1", 1), item(10, "1", 2))))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.DUPLICATE_ORDER_LINE);
        }

        @Test
        @DisplayName("una línea necesita producto, nombre, precio no negativo y cantidad positiva")
        void orderItem_withInvalidData_shouldThrow() {
            assertThatThrownBy(() -> item(1, "-1", 1)).isInstanceOf(InvalidRequestException.class);
            assertThatThrownBy(() -> item(1, "1", 0)).isInstanceOf(InvalidRequestException.class);
            assertThatThrownBy(() -> OrderItem.of(null, "x", BigDecimal.ONE, 1))
                    .isInstanceOf(InvalidRequestException.class);
            assertThatThrownBy(() -> OrderItem.of(1L, null, BigDecimal.ONE, 1))
                    .isInstanceOf(InvalidRequestException.class);
            assertThatThrownBy(() -> OrderItem.of(1L, "x", null, 1)).isInstanceOf(InvalidRequestException.class);
        }
    }

    @Nested
    @DisplayName("replaceItems")
    class ReplaceItems {

        @Test
        @DisplayName("sustituye las líneas de un pedido pendiente y recalcula el total")
        void replaceItems_pending_shouldRecalculate() {
            var order = pending();

            order.replaceItems(List.of(item(12, "7.25", 4)));

            assertThat(order.getTotal()).isEqualByComparingTo("29.00");
            assertThat(order.getItems()).extracting(OrderItem::productId).containsExactly(12L);
        }

        @Test
        @DisplayName("no permite cambiar un pedido que ya no está pendiente")
        void replaceItems_confirmed_shouldThrow() {
            var order = order(OrderStatus.CONFIRMED);

            assertThatThrownBy(() -> order.replaceItems(List.of(item(12, "1", 1))))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_NOT_MODIFIABLE);
        }
    }

    @Nested
    @DisplayName("cambios de estado")
    class StatusChanges {

        @ParameterizedTest(name = "{0} -> {1}: {2}")
        @CsvSource({
                "PENDING, CONFIRMED, true", "PENDING, REJECTED, true", "PENDING, CANCELLED, true",
                "CONFIRMED, CANCELLED, true", "CONFIRMED, REJECTED, false", "CONFIRMED, PENDING, false",
                "REJECTED, CANCELLED, false", "CANCELLED, CONFIRMED, false"})
        @DisplayName("solo se permiten las transiciones del ciclo de vida")
        void canTransitionTo_shouldFollowLifecycle(OrderStatus from, OrderStatus to, boolean allowed) {
            assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
        }

        @Test
        @DisplayName("confirm, reject y cancel cambian el estado")
        void transitions_shouldChangeStatus() {
            var confirmed = pending();
            var rejected = pending();
            confirmed.confirm();
            rejected.reject();
            confirmed.cancel();

            assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(rejected.getStatus()).isEqualTo(OrderStatus.REJECTED);
        }

        @Test
        @DisplayName("un pedido cancelado no se puede volver a cancelar")
        void cancel_cancelled_shouldThrow() {
            assertThatThrownBy(() -> order(OrderStatus.CANCELLED).cancel())
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.INVALID_STATUS_TRANSITION);
        }

        @Test
        @DisplayName("solo se borran pedidos en estado final")
        void deactivate_shouldRequireFinalStatus() {
            var cancelled = order(OrderStatus.CANCELLED);
            cancelled.deactivate();

            assertThat(cancelled.isActive()).isFalse();
            assertThatThrownBy(() -> pending().deactivate())
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_NOT_DELETABLE);
        }

        @Test
        @DisplayName("las líneas del pedido no se pueden modificar desde fuera")
        void items_shouldBeImmutable() {
            var items = pending().getItems();

            assertThatThrownBy(() -> items.add(item(1, "1", 1))).isInstanceOf(UnsupportedOperationException.class);
            assertThat(Collections.unmodifiableList(items)).hasSize(2);
        }
    }
}
