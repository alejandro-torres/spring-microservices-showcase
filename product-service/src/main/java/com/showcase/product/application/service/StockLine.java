package com.showcase.product.application.service;

/** Units of a product that an order asks to reserve or return. */
public record StockLine(Long productId, int quantity) {
}
