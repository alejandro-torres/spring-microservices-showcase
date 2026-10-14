package com.showcase.order.application.service;

/** Línea pedida por el cliente: el precio y el nombre se toman de product-service, nunca de la petición. */
public record OrderLine(Long productId, int quantity) {
}
