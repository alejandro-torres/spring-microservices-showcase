package com.showcase.order.application.service.impl;

import static com.showcase.order.testdata.OrderMother.customer;
import static com.showcase.order.testdata.OrderMother.order;
import static com.showcase.order.testdata.OrderMother.pending;
import static com.showcase.order.testdata.OrderMother.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.showcase.common.domain.exception.BusinessRuleException;
import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.ResourceNotFoundException;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.common.domain.page.SortDirection;
import com.showcase.order.application.client.CustomerClient;
import com.showcase.order.application.client.CustomerInfo;
import com.showcase.order.application.client.ProductClient;
import com.showcase.order.application.metrics.OrderMetrics;
import com.showcase.order.application.service.OrderLine;
import com.showcase.order.application.service.OrderWriter;
import com.showcase.order.domain.event.OrderCancelledEvent;
import com.showcase.order.domain.event.OrderEventLine;
import com.showcase.order.domain.exception.OrderErrorCode;
import com.showcase.order.domain.messaging.OrderEventPublisher;
import com.showcase.order.domain.model.Order;
import com.showcase.order.domain.model.OrderStatus;
import com.showcase.order.domain.model.ShippingAddress;
import com.showcase.order.domain.repository.OrderRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    private static final List<OrderLine> LINES = List.of(new OrderLine(10L, 2), new OrderLine(11L, 1));
    private static final ShippingAddress NEW_ADDRESS = new ShippingAddress("Calle Mayor 5", "Sevilla", "41001", "ES");
    private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderWriter orderWriter;

    @Mock
    private CustomerClient customerClient;

    @Mock
    private ProductClient productClient;

    @Mock
    private OrderEventPublisher eventPublisher;

    @Mock
    private OrderMetrics metrics;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(orderRepository, orderWriter, customerClient, productClient,
                eventPublisher, metrics, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void givenProducts() {
        given(productClient.findById(10L)).willReturn(Optional.of(product(10, "5", 50)));
        given(productClient.findById(11L)).willReturn(Optional.of(product(11, "20", 1)));
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("toma el precio de product-service y la dirección principal del cliente")
        void create_shouldBuildOrderFromRemoteData() {
            given(customerClient.findById(3L)).willReturn(Optional.of(customer(3)));
            givenProducts();
            given(orderWriter.create(any(Order.class))).willAnswer(invocation -> invocation.getArgument(0));

            var order = orderService.create(3L, LINES);

            assertThat(order.getTotal()).isEqualByComparingTo("30.00");
            assertThat(order.getShippingAddress().city()).isEqualTo("Madrid");
            assertThat(order.getItems()).extracting(item -> item.productName())
                    .containsExactly("Producto 10", "Producto 11");
        }

        @Test
        @DisplayName("lanza CUSTOMER_NOT_FOUND y no consulta los productos")
        void create_withUnknownCustomer_shouldThrow() {
            assertThatThrownBy(() -> orderService.create(3L, LINES))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.CUSTOMER_NOT_FOUND);
            verifyNoInteractions(productClient, orderWriter);
        }

        @Test
        @DisplayName("lanza CUSTOMER_WITHOUT_ADDRESS si el cliente no tiene dirección")
        void create_withCustomerWithoutAddress_shouldThrow() {
            given(customerClient.findById(3L))
                    .willReturn(Optional.of(new CustomerInfo(3L, "c@example.com", Optional.empty())));

            assertThatThrownBy(() -> orderService.create(3L, LINES))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.CUSTOMER_WITHOUT_ADDRESS);
        }

        @Test
        @DisplayName("lanza PRODUCT_NOT_FOUND si un producto no existe y no guarda nada")
        void create_withUnknownProduct_shouldThrow() {
            given(customerClient.findById(3L)).willReturn(Optional.of(customer(3)));
            given(productClient.findById(10L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.create(3L, LINES))
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.PRODUCT_NOT_FOUND);
            verifyNoInteractions(orderWriter);
        }

        @Test
        @DisplayName("lanza INSUFFICIENT_STOCK si no hay unidades suficientes")
        void create_withoutStock_shouldThrow() {
            given(customerClient.findById(3L)).willReturn(Optional.of(customer(3)));
            given(productClient.findById(10L)).willReturn(Optional.of(product(10, "5", 1)));

            assertThatThrownBy(() -> orderService.create(3L, LINES))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.INSUFFICIENT_STOCK);
        }
    }

    @Nested
    @DisplayName("consultas")
    class Queries {

        @Test
        @DisplayName("findById lanza ORDER_NOT_FOUND si no existe")
        void findById_missing_shouldThrow() {
            assertThatThrownBy(() -> orderService.findById(9L))
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_NOT_FOUND);
        }

        @Test
        @DisplayName("findAll delega en el repositorio")
        void findAll_shouldDelegate() {
            var query = new PageQuery(0, 10, "id", SortDirection.ASC);
            var page = PageResult.of(List.of(pending()), 0, 10, 1);
            given(orderRepository.findAll(List.of(), query, true)).willReturn(page);

            assertThat(orderService.findAll(List.of(), query, true)).isSameAs(page);
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("cambia la dirección de envío comprobando la versión, sin llamar a otros servicios")
        void update_shouldChangeShippingAddress() {
            given(orderRepository.findById(1L)).willReturn(Optional.of(order(OrderStatus.CONFIRMED)));
            given(orderRepository.save(any(Order.class))).willAnswer(invocation -> invocation.getArgument(0));

            var order = orderService.update(1L, NEW_ADDRESS, 0L);

            assertThat(order.getShippingAddress()).isEqualTo(NEW_ADDRESS);
            verifyNoInteractions(productClient, customerClient, orderWriter, eventPublisher);
        }

        @Test
        @DisplayName("lanza CONCURRENT_MODIFICATION si el pedido cambió desde que se leyó")
        void update_withStaleVersion_shouldThrow() {
            given(orderRepository.findById(1L)).willReturn(Optional.of(pending()));

            assertThatThrownBy(() -> orderService.update(1L, NEW_ADDRESS, 5L))
                    .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.CONCURRENT_MODIFICATION);
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("lanza ORDER_NOT_MODIFIABLE si el pedido está cerrado")
        void update_cancelled_shouldThrow() {
            given(orderRepository.findById(1L)).willReturn(Optional.of(order(OrderStatus.CANCELLED)));

            assertThatThrownBy(() -> orderService.update(1L, NEW_ADDRESS, 0L))
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_NOT_MODIFIABLE);
            verify(orderRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("cancel y delete")
    class CancelAndDelete {

        @Test
        @DisplayName("cancel guarda el pedido cancelado y publica OrderCancelledEvent para devolver el stock")
        void cancel_confirmed_shouldSaveAndPublish() {
            given(orderRepository.findById(1L)).willReturn(Optional.of(order(OrderStatus.CONFIRMED)));
            given(orderRepository.save(any(Order.class))).willAnswer(invocation -> invocation.getArgument(0));

            assertThat(orderService.cancel(1L).getStatus()).isEqualTo(OrderStatus.CANCELLED);
            verify(eventPublisher).publish(new OrderCancelledEvent(1L,
                    List.of(new OrderEventLine(10L, 2), new OrderEventLine(11L, 1)), NOW));
            verify(metrics).orderCancelled();
        }

        @Test
        @DisplayName("cancel rechaza un pedido pendiente con ORDER_NOT_CANCELLABLE y no publica nada")
        void cancel_pending_shouldThrowAndNotPublish() {
            given(orderRepository.findById(1L)).willReturn(Optional.of(pending()));

            assertThatThrownBy(() -> orderService.cancel(1L))
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_NOT_CANCELLABLE);
            verify(orderRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("delete hace un borrado lógico de un pedido cerrado")
        void delete_cancelled_shouldDeactivate() {
            given(orderRepository.findById(1L)).willReturn(Optional.of(order(OrderStatus.CANCELLED)));

            orderService.delete(1L);

            verify(orderRepository).save(argThat(order -> !order.isActive()));
        }

        @Test
        @DisplayName("delete no borra un pedido pendiente")
        void delete_pending_shouldThrow() {
            given(orderRepository.findById(1L)).willReturn(Optional.of(pending()));

            assertThatThrownBy(() -> orderService.delete(1L))
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_NOT_DELETABLE);
            verify(orderRepository, never()).save(any());
        }
    }
}
