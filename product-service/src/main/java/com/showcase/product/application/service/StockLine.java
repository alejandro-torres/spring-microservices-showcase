package com.showcase.product.application.service;

/** Unidades de un producto que pide reservar o devolver un pedido. */
public record StockLine(Long productId, int quantity) {
}
