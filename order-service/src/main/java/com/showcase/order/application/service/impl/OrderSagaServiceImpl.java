package com.showcase.order.application.service.impl;

import com.showcase.common.domain.exception.ResourceNotFoundException;
import com.showcase.common.domain.messaging.ProcessedEventRegistry;
import com.showcase.events.EventTypes;
import com.showcase.order.application.service.OrderSagaService;
import com.showcase.order.domain.exception.OrderErrorCode;
import com.showcase.order.domain.model.Order;
import com.showcase.order.domain.repository.OrderRepository;
import io.micrometer.observation.annotation.Observed;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cierra la saga de un pedido. El registro del evento procesado y el cambio de estado van en la misma
 * transacción: si el cambio falla, el rollback deshace también el registro y el reintento lo procesa de nuevo.
 * <p>
 * Un pedido que no existe o que ya no está PENDING es un error de datos que no se arregla reintentando: la
 * excepción de dominio hace que el mensaje vaya directo a la DLQ para revisarlo.
 */
@Service
public class OrderSagaServiceImpl implements OrderSagaService {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaServiceImpl.class);

    private final OrderRepository orderRepository;
    private final ProcessedEventRegistry processedEvents;

    public OrderSagaServiceImpl(OrderRepository orderRepository, ProcessedEventRegistry processedEvents) {
        this.orderRepository = orderRepository;
        this.processedEvents = processedEvents;
    }

    @Override
    @Transactional
    @Observed(name = "order.saga.confirm", contextualName = "confirmar pedido")
    public void confirm(UUID eventId, Long orderId) {
        apply(eventId, EventTypes.STOCK_RESERVED, orderId, Order::confirm);
    }

    @Override
    @Transactional
    @Observed(name = "order.saga.reject", contextualName = "rechazar pedido")
    public void reject(UUID eventId, Long orderId, String reason) {
        apply(eventId, EventTypes.STOCK_RESERVATION_FAILED, orderId, order -> order.reject(reason));
    }

    private void apply(UUID eventId, String eventType, Long orderId, Consumer<Order> change) {
        if (!processedEvents.registerIfAbsent(eventId, eventType)) {
            log.info("Evento {} ({}) ya procesado; se ignora el duplicado", eventId, eventType);
            return;
        }
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(OrderErrorCode.ORDER_NOT_FOUND, orderId));
        change.accept(order);
        orderRepository.save(order);
        log.info("Pedido {} pasa a {} por el evento {}", orderId, order.getStatus(), eventType);
    }
}
