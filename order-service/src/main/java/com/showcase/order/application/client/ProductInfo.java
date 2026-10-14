package com.showcase.order.application.client;

import java.math.BigDecimal;

/** Lo que order-service necesita de un producto. */
public record ProductInfo(Long id, String name, BigDecimal price, int stock) {
}
