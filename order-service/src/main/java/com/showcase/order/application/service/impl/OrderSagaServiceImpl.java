package com.showcase.order.application.service.impl;

import com.showcase.common.domain.exception.ResourceNotFoundException;
import com.showcase.common.domain.messaging.ProcessedEventRegistry;
import com.showcase.events.EventTypes;
import com.showcase.order.application.metrics.OrderMetrics;
import com.showcase.order.application.service.OrderSagaService;
import com.showcase.order.domain.exception.OrderErrorCode;
import com.showcase.order.domain.model.Order;
import com.showcase.order.domain.repository.OrderRepository;
import io.micrometer.observation.annotation.Observed;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Closes an order's saga. Registering the processed event and changing the status happen in the same
 * transaction: if the change fails, the rollback also undoes the registration and the retry processes it again.
 * <p>
 * An order that does not exist or is no longer PENDING is a data error that retrying will not fix: the domain
 * exception sends the message straight to the DLQ for review.
 */
@Service
@RequiredArgsConstructor
public class OrderSagaServiceImpl implements OrderSagaService {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaServiceImpl.class);

    private final OrderRepository orderRepository;
    private final ProcessedEventRegistry processedEvents;
    private final OrderMetrics metrics;

    @Override
    @Transactional
    @Observed(name = "order.saga.confirm", contextualName = "confirm order")
    public void confirm(UUID eventId, Long orderId) {
        if (apply(eventId, EventTypes.STOCK_RESERVED, orderId, Order::confirm)) {
            metrics.orderConfirmed();
        }
    }

    @Override
    @Transactional
    @Observed(name = "order.saga.reject", contextualName = "reject order")
    public void reject(UUID eventId, Long orderId, String reason) {
        if (apply(eventId, EventTypes.STOCK_RESERVATION_FAILED, orderId, order -> order.reject(reason))) {
            metrics.orderRejected(reason);
        }
    }

    /** @return whether the event was new and has been applied (false for a duplicate) */
    private boolean apply(UUID eventId, String eventType, Long orderId, Consumer<Order> change) {
        if (!processedEvents.registerIfAbsent(eventId, eventType)) {
            log.info("Event {} ({}) already processed; ignoring the duplicate", eventId, eventType);
            return false;
        }
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(OrderErrorCode.ORDER_NOT_FOUND, orderId));
        change.accept(order);
        orderRepository.save(order);
        log.info("Order {} moves to {} because of event {}", orderId, order.getStatus(), eventType);
        return true;
    }
}
