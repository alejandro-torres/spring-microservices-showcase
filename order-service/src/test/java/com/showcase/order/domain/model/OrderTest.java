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
    @DisplayName("changeShippingAddress")
    class ChangeShippingAddress {

        private final ShippingAddress newAddress = new ShippingAddress("Calle Mayor 5", "Sevilla", "41001", "ES");

        @ParameterizedTest(name = "{0}")
        @CsvSource({"PENDING", "CONFIRMED"})
        @DisplayName("cambia la dirección de un pedido abierto sin tocar sus líneas")
        void changeShippingAddress_openOrder_shouldChangeAddress(OrderStatus status) {
            var order = order(status);

            order.changeShippingAddress(newAddress);

            assertThat(order.getShippingAddress()).isEqualTo(newAddress);
            assertThat(order.getTotal()).isEqualByComparingTo("30.00");
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({"REJECTED", "CANCELLED"})
        @DisplayName("no permite cambiar un pedido cerrado")
        void changeShippingAddress_closedOrder_shouldThrow(OrderStatus status) {
            var order = order(status);

            assertThatThrownBy(() -> order.changeShippingAddress(newAddress))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_NOT_MODIFIABLE);
        }

        @Test
        @DisplayName("una dirección necesita calle, ciudad, código postal y país de 2 letras en mayúsculas")
        void shippingAddress_withInvalidData_shouldThrow() {
            assertThatThrownBy(() -> new ShippingAddress(" ", "Madrid", "28013", "ES"))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.INVALID_ORDER_DATA);
            assertThatThrownBy(() -> new ShippingAddress("Gran Vía 1", "Madrid", "28013", "es"))
                    .isInstanceOf(InvalidRequestException.class);
            assertThatThrownBy(() -> new ShippingAddress("Gran Vía 1", null, "28013", "ES"))
                    .isInstanceOf(InvalidRequestException.class);
        }
    }

    @Nested
    @DisplayName("cambios de estado")
    class StatusChanges {

        @ParameterizedTest(name = "{0} -> {1}: {2}")
        @CsvSource({
                "PENDING, CONFIRMED, true", "PENDING, REJECTED, true", "PENDING, CANCELLED, false",
                "CONFIRMED, CANCELLED, true", "CONFIRMED, REJECTED, false", "CONFIRMED, PENDING, false",
                "REJECTED, CANCELLED, false", "CANCELLED, CONFIRMED, false"})
        @DisplayName("solo se permiten las transiciones del ciclo de vida")
        void canTransitionTo_shouldFollowLifecycle(OrderStatus from, OrderStatus to, boolean allowed) {
            assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
        }

        @Test
        @DisplayName("confirm y cancel cambian el estado; reject guarda además el motivo")
        void transitions_shouldChangeStatus() {
            var confirmed = pending();
            var rejected = pending();
            confirmed.confirm();
            rejected.reject("INSUFFICIENT_STOCK");
            confirmed.cancel();

            assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(rejected.getStatus()).isEqualTo(OrderStatus.REJECTED);
            assertThat(rejected.getRejectionReason()).isEqualTo("INSUFFICIENT_STOCK");
        }

        @Test
        @DisplayName("un motivo de rechazo demasiado largo se recorta a 100 caracteres")
        void reject_withLongReason_shouldTruncate() {
            var order = pending();

            order.reject("X".repeat(150));

            assertThat(order.getRejectionReason()).hasSize(Order.MAX_REASON);
        }

        @Test
        @DisplayName("un pedido ya confirmado no se puede rechazar")
        void reject_confirmed_shouldThrow() {
            assertThatThrownBy(() -> order(OrderStatus.CONFIRMED).reject("INSUFFICIENT_STOCK"))
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.INVALID_STATUS_TRANSITION);
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({"PENDING", "REJECTED", "CANCELLED"})
        @DisplayName("solo se cancela un pedido confirmado: uno pendiente aún está en la saga")
        void cancel_notConfirmed_shouldThrowNotCancellable(OrderStatus status) {
            assertThatThrownBy(() -> order(status).cancel())
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_NOT_CANCELLABLE);
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
