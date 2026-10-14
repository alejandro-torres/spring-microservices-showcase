package com.showcase.order.domain.model;

import com.showcase.common.domain.exception.BusinessRuleException;
import com.showcase.common.domain.exception.InvalidRequestException;
import com.showcase.common.domain.model.AuditedModel;
import com.showcase.order.domain.exception.OrderErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pedido de un cliente. Solo se pueden cambiar sus líneas mientras está pendiente; el total se recalcula
 * siempre a partir de ellas.
 */
public class Order extends AuditedModel {

    public static final int MAX_ITEMS = 20;

    private final Long customerId;
    private final ShippingAddress shippingAddress;
    private OrderStatus status;
    private List<OrderItem> items;

    public Order(Long id, Long customerId, ShippingAddress shippingAddress, OrderStatus status, List<OrderItem> items,
                 boolean active, Long version, Instant createdAt, Instant updatedAt) {
        super(id, active, version, createdAt, updatedAt);
        this.customerId = customerId;
        this.shippingAddress = shippingAddress;
        this.status = status;
        this.items = List.copyOf(items);
    }

    public static Order create(Long customerId, ShippingAddress shippingAddress, List<OrderItem> items) {
        if (customerId == null || shippingAddress == null) {
            throw new InvalidRequestException(OrderErrorCode.INVALID_ORDER_DATA, "customerId");
        }
        return new Order(null, customerId, shippingAddress, OrderStatus.PENDING, validItems(items), true, null,
                null, null);
    }

    public void replaceItems(List<OrderItem> newItems) {
        if (status != OrderStatus.PENDING) {
            throw new BusinessRuleException(OrderErrorCode.ORDER_NOT_MODIFIABLE, getId(), status);
        }
        this.items = validItems(newItems);
    }

    public void confirm() {
        transitionTo(OrderStatus.CONFIRMED);
    }

    public void reject() {
        transitionTo(OrderStatus.REJECTED);
    }

    public void cancel() {
        transitionTo(OrderStatus.CANCELLED);
    }

    /** Solo se borran (lógicamente) los pedidos cerrados; los demás todavía pueden cambiar. */
    @Override
    public void deactivate() {
        if (!status.isFinal()) {
            throw new BusinessRuleException(OrderErrorCode.ORDER_NOT_DELETABLE, getId(), status);
        }
        super.deactivate();
    }

    private void transitionTo(OrderStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new BusinessRuleException(OrderErrorCode.INVALID_STATUS_TRANSITION, status, target);
        }
        this.status = target;
    }

    private static List<OrderItem> validItems(List<OrderItem> items) {
        if (items == null || items.isEmpty() || items.size() > MAX_ITEMS) {
            throw new InvalidRequestException(OrderErrorCode.INVALID_ORDER_DATA, "items");
        }
        Set<Long> productIds = new HashSet<>();
        for (OrderItem item : items) {
            if (!productIds.add(item.productId())) {
                throw new InvalidRequestException(OrderErrorCode.DUPLICATE_ORDER_LINE, item.productId());
            }
        }
        return List.copyOf(items);
    }

    public BigDecimal getTotal() {
        return items.stream().map(OrderItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean containsProduct(Long productId) {
        return items.stream().anyMatch(item -> Objects.equals(item.productId(), productId));
    }

    public Long getCustomerId() {
        return customerId;
    }

    public ShippingAddress getShippingAddress() {
        return shippingAddress;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public List<OrderItem> getItems() {
        return items;
    }
}
