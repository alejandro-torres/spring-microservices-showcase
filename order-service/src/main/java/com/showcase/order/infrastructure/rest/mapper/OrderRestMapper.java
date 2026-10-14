package com.showcase.order.infrastructure.rest.mapper;

import com.showcase.order.application.service.OrderLine;
import com.showcase.order.domain.model.Order;
import com.showcase.order.domain.model.OrderItem;
import com.showcase.order.domain.model.ShippingAddress;
import com.showcase.order.infrastructure.rest.dto.request.OrderLineRequestDTO;
import com.showcase.order.infrastructure.rest.dto.request.UpdateOrderRequestDTO;
import com.showcase.order.infrastructure.rest.dto.response.OrderItemResponseDTO;
import com.showcase.order.infrastructure.rest.dto.response.OrderResponseDTO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderRestMapper {

    OrderResponseDTO toResponse(Order order);

    @Mapping(target = "lineTotal", expression = "java(item.lineTotal())")
    OrderItemResponseDTO toResponse(OrderItem item);

    OrderLine toLine(OrderLineRequestDTO request);

    List<OrderLine> toLines(List<OrderLineRequestDTO> requests);

    ShippingAddress toShippingAddress(UpdateOrderRequestDTO request);
}
