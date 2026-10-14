package com.showcase.product.infrastructure.messaging.mapper;

import com.showcase.events.order.OrderLineMessageDTO;
import com.showcase.events.product.StockReleasedMessageDTO;
import com.showcase.events.product.StockReservationFailedMessageDTO;
import com.showcase.events.product.StockReservedMessageDTO;
import com.showcase.product.application.service.StockLine;
import com.showcase.product.domain.event.StockReleasedEvent;
import com.showcase.product.domain.event.StockReservationFailedEvent;
import com.showcase.product.domain.event.StockReservedEvent;
import java.util.List;
import org.mapstruct.Mapper;

/** Eventos de dominio de stock ↔ MessageDTO de events-contract. */
@Mapper(componentModel = "spring")
public interface StockMessageMapper {

    StockReservedMessageDTO toMessage(StockReservedEvent event);

    StockReservationFailedMessageDTO toMessage(StockReservationFailedEvent event);

    StockReleasedMessageDTO toMessage(StockReleasedEvent event);

    StockLine toStockLine(OrderLineMessageDTO line);

    List<StockLine> toStockLines(List<OrderLineMessageDTO> lines);
}
