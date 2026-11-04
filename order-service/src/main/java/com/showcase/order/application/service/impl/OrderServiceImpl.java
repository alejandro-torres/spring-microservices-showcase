package com.showcase.order.application.service.impl;

import com.showcase.common.domain.exception.BusinessRuleException;
import com.showcase.common.domain.exception.ResourceNotFoundException;
import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.order.application.client.CustomerClient;
import com.showcase.order.application.client.ProductClient;
import com.showcase.order.application.client.ProductInfo;
import com.showcase.order.application.metrics.OrderMetrics;
import com.showcase.order.application.service.OrderLine;
import com.showcase.order.application.service.OrderService;
import com.showcase.order.application.service.OrderWriter;
import com.showcase.order.domain.event.OrderCancelledEvent;
import com.showcase.order.domain.exception.OrderErrorCode;
import com.showcase.order.domain.messaging.OrderEventPublisher;
import com.showcase.order.domain.model.Order;
import com.showcase.order.domain.model.OrderItem;
import com.showcase.order.domain.model.ShippingAddress;
import com.showcase.order.domain.repository.OrderRepository;
import io.micrometer.observation.annotation.Observed;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creating an order requires querying customer-service and product-service. Those calls can take
 * seconds (or fail by timeout), so they run WITHOUT a transaction ({@code NOT_SUPPORTED}) so as not to hold
 * a database connection in the meantime; afterwards, {@link OrderWriter} saves in a short transaction.
 * <p>
 * {@code OrderWriter} is a separate bean on purpose: if its {@code @Transactional} method were in this class and
 * called with {@code this.save(...)}, the call would not go through the Spring proxy and the annotation would be
 * ignored (self-invocation).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderWriter orderWriter;
    private final CustomerClient customerClient;
    private final ProductClient productClient;
    private final OrderEventPublisher eventPublisher;
    private final OrderMetrics metrics;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Observed(name = "order.create", contextualName = "create order")
    public Order create(Long customerId, List<OrderLine> lines) {
        ShippingAddress address = customerClient.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException(OrderErrorCode.CUSTOMER_NOT_FOUND, customerId))
                .mainAddress()
                .orElseThrow(() -> new BusinessRuleException(OrderErrorCode.CUSTOMER_WITHOUT_ADDRESS, customerId));
        return orderWriter.create(Order.create(customerId, address, toItems(lines)));
    }

    @Override
    public Order findById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(OrderErrorCode.ORDER_NOT_FOUND, id));
    }

    @Override
    public PageResult<Order> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive) {
        return orderRepository.findAll(filters, pageQuery, includeInactive);
    }

    @Override
    @Transactional
    public Order update(Long id, ShippingAddress shippingAddress, Long version) {
        Order order = findById(id);
        order.checkVersion(version);
        order.changeShippingAddress(shippingAddress);
        return orderRepository.save(order);
    }

    /**
     * The status change and the {@code OrderCancelledEvent} are saved together: either it is cancelled and the
     * stock return is requested, or neither happens.
     */
    @Override
    @Transactional
    @Observed(name = "order.cancel", contextualName = "cancel order")
    public Order cancel(Long id) {
        Order order = findById(id);
        order.cancel();
        Order saved = orderRepository.save(order);
        eventPublisher.publish(OrderCancelledEvent.of(saved, Instant.now(clock)));
        metrics.orderCancelled();
        return saved;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Order order = findById(id);
        order.deactivate();
        orderRepository.save(order);
    }

    /**
     * The stock is checked here only to fail fast: the real (atomic) reservation is done by product-service
     * in the order saga.
     */
    private List<OrderItem> toItems(List<OrderLine> lines) {
        return lines.stream().map(line -> {
            ProductInfo product = productClient.findById(line.productId()).orElseThrow(
                    () -> new ResourceNotFoundException(OrderErrorCode.PRODUCT_NOT_FOUND, line.productId()));
            if (product.stock() < line.quantity()) {
                throw new BusinessRuleException(OrderErrorCode.INSUFFICIENT_STOCK, product.id(), product.stock());
            }
            return OrderItem.of(product.id(), product.name(), product.price(), line.quantity());
        }).toList();
    }
}
