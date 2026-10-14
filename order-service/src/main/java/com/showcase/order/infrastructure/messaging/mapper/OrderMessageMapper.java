package com.showcase.order.infrastructure.messaging.mapper;

import com.showcase.events.order.OrderCancelledMessageDTO;
import com.showcase.events.order.OrderCreatedMessageDTO;
import com.showcase.events.order.OrderLineMessageDTO;
import com.showcase.order.domain.event.OrderCancelledEvent;
import com.showcase.order.domain.event.OrderCreatedEvent;
import com.showcase.order.domain.event.OrderEventLine;
import org.mapstruct.Mapper;

/**
 * Eventos de dominio (internos) → MessageDTO (contrato público de events-contract). Separarlos permite cambiar
 * el dominio sin romper a los consumidores, y al revés.
 */
@Mapper(componentModel = "spring")
public interface OrderMessageMapper {

    OrderCreatedMessageDTO toMessage(OrderCreatedEvent event);

    OrderCancelledMessageDTO toMessage(OrderCancelledEvent event);

    OrderLineMessageDTO toMessage(OrderEventLine line);
}
