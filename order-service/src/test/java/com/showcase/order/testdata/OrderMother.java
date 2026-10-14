package com.showcase.order.testdata;

import com.showcase.order.application.client.CustomerInfo;
import com.showcase.order.application.client.ProductInfo;
import com.showcase.order.domain.model.Order;
import com.showcase.order.domain.model.OrderItem;
import com.showcase.order.domain.model.OrderStatus;
import com.showcase.order.domain.model.ShippingAddress;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class OrderMother {

    public static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");
    public static final ShippingAddress ADDRESS = new ShippingAddress("Gran Vía 1", "Madrid", "28013", "ES");

    private OrderMother() {
    }

    /** Pedido 1 del cliente 3: 2 x producto 10 (5,00) + 1 x producto 11 (20,00) = 30,00. */
    public static Order order(OrderStatus status) {
        return new Order(1L, 3L, ADDRESS, status,
                List.of(new OrderItem(100L, 10L, "Ratón", new BigDecimal("5.00"), 2),
                        new OrderItem(101L, 11L, "Teclado", new BigDecimal("20.00"), 1)),
                true, 0L, CREATED_AT, CREATED_AT);
    }

    public static Order pending() {
        return order(OrderStatus.PENDING);
    }

    public static OrderItem item(long productId, String price, int quantity) {
        return OrderItem.of(productId, "Producto " + productId, new BigDecimal(price), quantity);
    }

    public static ProductInfo product(long id, String price, int stock) {
        return new ProductInfo(id, "Producto " + id, new BigDecimal(price), stock);
    }

    public static CustomerInfo customer(long id) {
        return new CustomerInfo(id, "cliente" + id + "@example.com", Optional.of(ADDRESS));
    }
}
