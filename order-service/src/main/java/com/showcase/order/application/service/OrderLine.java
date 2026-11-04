package com.showcase.order.application.service;

/** Line requested by the customer: the price and name come from product-service, never from the request. */
public record OrderLine(Long productId, int quantity) {
}
