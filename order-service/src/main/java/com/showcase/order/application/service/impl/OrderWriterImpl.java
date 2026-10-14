package com.showcase.order.application.service.impl;

import com.showcase.order.application.metrics.OrderMetrics;
import com.showcase.order.application.service.OrderWriter;
import com.showcase.order.domain.event.OrderCreatedEvent;
import com.showcase.order.domain.messaging.OrderEventPublisher;
import com.showcase.order.domain.model.Order;
import com.showcase.order.domain.repository.OrderRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderWriterImpl implements OrderWriter {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;
    private final OrderMetrics metrics;
    private final Clock clock;

    public OrderWriterImpl(OrderRepository orderRepository, OrderEventPublisher eventPublisher, OrderMetrics metrics,
                           Clock clock) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
        this.clock = clock;
    }

    /**
     * El pedido, sus líneas (cascada) y su evento en el outbox se guardan en la MISMA transacción: si algo falla
     * no queda ni el pedido ni el evento, y nunca se publica un pedido que no se llegó a guardar.
     * {@code timeout = 5}: con las llamadas remotas ya hechas, guardar nunca debería tardar más.
     */
    @Override
    @Transactional(timeout = 5)
    public Order create(Order order) {
        Order saved = orderRepository.save(order);
        eventPublisher.publish(OrderCreatedEvent.of(saved, Instant.now(clock)));
        metrics.orderCreated();
        return saved;
    }
}
