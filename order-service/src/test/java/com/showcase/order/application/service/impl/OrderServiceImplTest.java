package com.showcase.order.application.service.impl;

import static com.showcase.order.testdata.OrderMother.customer;
import static com.showcase.order.testdata.OrderMother.order;
import static com.showcase.order.testdata.OrderMother.pending;
import static com.showcase.order.testdata.OrderMother.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.showcase.common.domain.exception.BusinessRuleException;
import com.showcase.common.domain.exception.ResourceNotFoundException;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.common.domain.page.SortDirection;
import com.showcase.order.application.client.CustomerClient;
import com.showcase.order.application.client.CustomerInfo;
import com.showcase.order.application.client.ProductClient;
import com.showcase.order.application.service.OrderLine;
import com.showcase.order.application.service.OrderWriter;
import com.showcase.order.domain.exception.OrderErrorCode;
import com.showcase.order.domain.model.Order;
import com.showcase.order.domain.model.OrderStatus;
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
class OrderServiceImplTest {

    private static final List<OrderLine> LINES = List.of(new OrderLine(10L, 2), new OrderLine(11L, 1));

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderWriter orderWriter;

    @Mock
    private CustomerClient customerClient;

    @Mock
    private ProductClient productClient;

    @InjectMocks
    private OrderServiceImpl orderService;

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
        @DisplayName("consulta los productos y delega en OrderWriter con la versión")
        void update_pending_shouldReplaceItems() {
            given(orderRepository.findById(1L)).willReturn(Optional.of(pending()));
            givenProducts();
            given(orderWriter.replaceItems(eq(1L), eq(0L), anyList())).willReturn(pending());

            orderService.update(1L, LINES, 0L);

            verify(orderWriter).replaceItems(eq(1L), eq(0L), argThat(items -> items.size() == 2));
        }

        @Test
        @DisplayName("no llama a product-service si el pedido ya no es modificable")
        void update_confirmed_shouldFailFast() {
            given(orderRepository.findById(1L)).willReturn(Optional.of(order(OrderStatus.CONFIRMED)));

            assertThatThrownBy(() -> orderService.update(1L, LINES, 0L))
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_NOT_MODIFIABLE);
            verifyNoInteractions(productClient, orderWriter);
        }
    }

    @Nested
    @DisplayName("cancel y delete")
    class CancelAndDelete {

        @Test
        @DisplayName("cancel guarda el pedido cancelado")
        void cancel_shouldSaveCancelled() {
            given(orderRepository.findById(1L)).willReturn(Optional.of(pending()));
            given(orderRepository.save(any(Order.class))).willAnswer(invocation -> invocation.getArgument(0));

            assertThat(orderService.cancel(1L).getStatus()).isEqualTo(OrderStatus.CANCELLED);
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
