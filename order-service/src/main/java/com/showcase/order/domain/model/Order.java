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
 * Pedido de un cliente. Las líneas quedan fijas al crearlo: son las que product-service reserva en la saga, y
 * cambiarlas después dejaría la reserva desalineada con el pedido. Lo único modificable es la dirección de envío,
 * mientras el pedido no esté cerrado.
 */
public class Order extends AuditedModel {

    public static final int MAX_ITEMS = 20;
    static final int MAX_REASON = 100;

    private final Long customerId;
    private ShippingAddress shippingAddress;
    private OrderStatus status;
    private String rejectionReason;
    private final List<OrderItem> items;

    public Order(Long id, Long customerId, ShippingAddress shippingAddress, OrderStatus status, String rejectionReason,
                 List<OrderItem> items, boolean active, Long version, Instant createdAt, Instant updatedAt) {
        super(id, active, version, createdAt, updatedAt);
        this.customerId = customerId;
        this.shippingAddress = shippingAddress;
        this.status = status;
        this.rejectionReason = rejectionReason;
        this.items = List.copyOf(items);
    }

    public static Order create(Long customerId, ShippingAddress shippingAddress, List<OrderItem> items) {
        if (customerId == null || shippingAddress == null) {
            throw new InvalidRequestException(OrderErrorCode.INVALID_ORDER_DATA, "customerId");
        }
        return new Order(null, customerId, shippingAddress, OrderStatus.PENDING, null, validItems(items), true, null,
                null, null);
    }

    public void changeShippingAddress(ShippingAddress newAddress) {
        if (status.isFinal()) {
            throw new BusinessRuleException(OrderErrorCode.ORDER_NOT_MODIFIABLE, getId(), status);
        }
        this.shippingAddress = Objects.requireNonNull(newAddress, "shippingAddress");
    }

    /** product-service ha reservado el stock de todas las líneas. */
    public void confirm() {
        transitionTo(OrderStatus.CONFIRMED);
    }

    /** product-service no ha podido reservar el stock; {@code reason} es el código que explica por qué. */
    public void reject(String reason) {
        transitionTo(OrderStatus.REJECTED);
        this.rejectionReason = reason == null || reason.length() <= MAX_REASON ? reason
                : reason.substring(0, MAX_REASON);
    }

    /**
     * Solo se cancela un pedido confirmado: su stock está reservado y la cancelación lo devuelve. Un pedido
     * pendiente todavía está en plena saga (no se sabe si se reservará), así que no se puede cancelar.
     */
    public void cancel() {
        if (status != OrderStatus.CONFIRMED) {
            throw new BusinessRuleException(OrderErrorCode.ORDER_NOT_CANCELLABLE, getId(), status);
        }
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

    public String getRejectionReason() {
        return rejectionReason;
    }

    public List<OrderItem> getItems() {
        return items;
    }
}
