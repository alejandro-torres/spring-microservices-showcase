package com.showcase.order.application.client;

import java.math.BigDecimal;

/** What order-service needs from a product. */
public record ProductInfo(Long id, String name, BigDecimal price, int stock) {
}
