package com.showcase.order.application.service.impl;

import com.showcase.order.application.metrics.OrderMetrics;
import com.showcase.order.application.service.OrderWriter;
import com.showcase.order.domain.event.OrderCreatedEvent;
import com.showcase.order.domain.messaging.OrderEventPublisher;
import com.showcase.order.domain.model.Order;
import com.showcase.order.domain.repository.OrderRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderWriterImpl implements OrderWriter {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;
    private final OrderMetrics metrics;
    private final Clock clock;

    /**
     * The order, its lines (cascade) and its outbox event are saved in the SAME transaction: if anything fails,
     * neither the order nor the event remains, and an order that was never saved is never published.
     * {@code timeout = 5}: with the remote calls already done, saving should never take longer.
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
