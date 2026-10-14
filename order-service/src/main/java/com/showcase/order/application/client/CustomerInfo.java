package com.showcase.order.application.client;

import com.showcase.order.domain.model.ShippingAddress;
import java.util.Optional;

/** Lo que order-service necesita de un cliente: su dirección principal, si tiene. */
public record CustomerInfo(Long id, String email, Optional<ShippingAddress> mainAddress) {
}
