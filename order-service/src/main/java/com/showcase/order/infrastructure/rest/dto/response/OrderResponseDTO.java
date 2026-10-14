package com.showcase.order.infrastructure.rest.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponseDTO(
        Long id,
        Long customerId,
        String status,
        String rejectionReason,
        BigDecimal total,
        ShippingAddressResponseDTO shippingAddress,
        List<OrderItemResponseDTO> items,
        boolean active,
        Long version,
        Instant createdAt,
        Instant updatedAt) {
}
