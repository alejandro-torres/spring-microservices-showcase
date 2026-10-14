package com.showcase.order.infrastructure.persistence.mapper;

import com.showcase.order.domain.model.Order;
import com.showcase.order.domain.model.OrderItem;
import com.showcase.order.infrastructure.persistence.entity.OrderEntity;
import com.showcase.order.infrastructure.persistence.entity.OrderItemEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/** Las líneas se sincronizan a mano en el repositorio (ver {@code OrderRepositoryImpl}). */
@Mapper(componentModel = "spring")
public interface OrderEntityMapper {

    @Mapping(target = "shippingAddress", expression = "java(new com.showcase.order.domain.model.ShippingAddress("
            + "entity.getShippingStreet(), entity.getShippingCity(), entity.getShippingPostalCode(), "
            + "entity.getShippingCountry()))")
    Order toDomain(OrderEntity entity);

    OrderItem toDomain(OrderItemEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "shippingStreet", source = "shippingAddress.street")
    @Mapping(target = "shippingCity", source = "shippingAddress.city")
    @Mapping(target = "shippingPostalCode", source = "shippingAddress.postalCode")
    @Mapping(target = "shippingCountry", source = "shippingAddress.country")
    void copyToEntity(Order order, @MappingTarget OrderEntity entity);
}
