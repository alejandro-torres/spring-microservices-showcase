package com.showcase.order.domain.model;

import com.showcase.common.domain.exception.InvalidRequestException;
import com.showcase.order.domain.exception.OrderErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Línea de un pedido. Guarda el nombre y el precio del producto en el momento de la compra: si después
 * cambian en el catálogo, el pedido no se altera.
 */
public record OrderItem(Long id, Long productId, String productName, BigDecimal unitPrice, int quantity) {

    public OrderItem {
        if (productId == null || productName == null || unitPrice == null || unitPrice.signum() < 0 || quantity < 1) {
            throw new InvalidRequestException(OrderErrorCode.INVALID_ORDER_DATA, "items");
        }
        unitPrice = unitPrice.setScale(2, RoundingMode.HALF_UP);
    }

    public static OrderItem of(Long productId, String productName, BigDecimal unitPrice, int quantity) {
        return new OrderItem(null, productId, productName, unitPrice, quantity);
    }

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
