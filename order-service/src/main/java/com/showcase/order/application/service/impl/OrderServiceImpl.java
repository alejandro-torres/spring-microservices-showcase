package com.showcase.order.application.service.impl;

import com.showcase.common.domain.exception.BusinessRuleException;
import com.showcase.common.domain.exception.ResourceNotFoundException;
import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.order.application.client.CustomerClient;
import com.showcase.order.application.client.ProductClient;
import com.showcase.order.application.client.ProductInfo;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crear un pedido exige consultar customer-service y product-service. Esas llamadas pueden tardar
 * segundos (o fallar por timeout), así que se hacen SIN transacción ({@code NOT_SUPPORTED}) para no tener
 * una conexión a la base de datos bloqueada mientras tanto; después, {@link OrderWriter} guarda en una
 * transacción corta.
 * <p>
 * {@code OrderWriter} es otro bean a propósito: si su método {@code @Transactional} estuviera en esta clase y se
 * llamara con {@code this.save(...)}, la llamada no pasaría por el proxy de Spring y la anotación se ignoraría
 * (autoinvocación).
 */
@Service
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderWriter orderWriter;
    private final CustomerClient customerClient;
    private final ProductClient productClient;
    private final OrderEventPublisher eventPublisher;
    private final Clock clock;

    public OrderServiceImpl(OrderRepository orderRepository, OrderWriter orderWriter, CustomerClient customerClient,
                            ProductClient productClient, OrderEventPublisher eventPublisher, Clock clock) {
        this.orderRepository = orderRepository;
        this.orderWriter = orderWriter;
        this.customerClient = customerClient;
        this.productClient = productClient;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Observed(name = "order.create", contextualName = "crear pedido")
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
     * El cambio de estado y el {@code OrderCancelledEvent} se guardan juntos: o se cancela y se pide devolver el
     * stock, o no pasa ninguna de las dos cosas.
     */
    @Override
    @Transactional
    @Observed(name = "order.cancel", contextualName = "cancelar pedido")
    public Order cancel(Long id) {
        Order order = findById(id);
        order.cancel();
        Order saved = orderRepository.save(order);
        eventPublisher.publish(OrderCancelledEvent.of(saved, Instant.now(clock)));
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
     * El stock se comprueba aquí solo para fallar pronto: la reserva real (atómica) la hará product-service
     * en la saga del pedido.
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
