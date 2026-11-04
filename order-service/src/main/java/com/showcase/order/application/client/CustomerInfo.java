package com.showcase.order.application.client;

import com.showcase.order.domain.model.ShippingAddress;
import java.util.Optional;

/** What order-service needs from a customer: their primary address, if any. */
public record CustomerInfo(Long id, String email, Optional<ShippingAddress> mainAddress) {
}
