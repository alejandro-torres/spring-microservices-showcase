package com.showcase.order.infrastructure.persistence.mapper;

import static com.showcase.order.testdata.OrderMother.ADDRESS;
import static com.showcase.order.testdata.OrderMother.order;
import static org.assertj.core.api.Assertions.assertThat;

import com.showcase.order.domain.model.Order;
import com.showcase.order.domain.model.OrderStatus;
import com.showcase.order.infrastructure.persistence.entity.OrderEntity;
import com.showcase.order.infrastructure.persistence.entity.OrderItemEntity;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class OrderEntityMapperTest {

    private final OrderEntityMapper mapper = Mappers.getMapper(OrderEntityMapper.class);

    @Nested
    @DisplayName("toDomain")
    class ToDomain {

        @Test
        @DisplayName("reconstruye el pedido con la dirección de envío, el estado, el motivo y sus líneas")
        void toDomain_shouldMapEveryField() {
            // given
            var entity = new OrderEntity();
            entity.setId(7L);
            entity.setCustomerId(3L);
            entity.setStatus(OrderStatus.REJECTED);
            entity.setRejectionReason("INSUFFICIENT_STOCK");
            entity.setTotal(new BigDecimal("10.00"));
            entity.setShippingStreet("Gran Vía 1");
            entity.setShippingCity("Madrid");
            entity.setShippingPostalCode("28013");
            entity.setShippingCountry("ES");
            entity.getItems().add(new OrderItemEntity(entity, 10L, "Ratón", new BigDecimal("5.00"), 2));

            // when
            Order order = mapper.toDomain(entity);

            // then
            assertThat(order.getId()).isEqualTo(7L);
            assertThat(order.getCustomerId()).isEqualTo(3L);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
            assertThat(order.getRejectionReason()).isEqualTo("INSUFFICIENT_STOCK");
            assertThat(order.getShippingAddress()).isEqualTo(ADDRESS);
            assertThat(order.getItems()).singleElement().satisfies(item -> {
                assertThat(item.productId()).isEqualTo(10L);
                assertThat(item.productName()).isEqualTo("Ratón");
                assertThat(item.quantity()).isEqualTo(2);
            });
            assertThat(order.getTotal()).isEqualByComparingTo("10.00");
        }
    }

    @Nested
    @DisplayName("copyToEntity")
    class CopyToEntity {

        @Test
        @DisplayName("copia los campos editables y la dirección desglosada, sin tocar el id ni las líneas")
        void copyToEntity_shouldCopyEditableFields() {
            // given
            var entity = new OrderEntity();
            entity.setId(99L);
            Order confirmed = order(OrderStatus.CONFIRMED);

            // when
            mapper.copyToEntity(confirmed, entity);

            // then
            assertThat(entity.getId()).isEqualTo(99L);
            assertThat(entity.getItems()).isEmpty();
            assertThat(entity.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(entity.getRejectionReason()).isNull();
            assertThat(entity.getTotal()).isEqualByComparingTo("30.00");
            assertThat(entity.getShippingStreet()).isEqualTo("Gran Vía 1");
            assertThat(entity.getShippingCountry()).isEqualTo("ES");
        }
    }
}
