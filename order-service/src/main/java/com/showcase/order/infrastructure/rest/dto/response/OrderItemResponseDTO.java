package com.showcase.order.infrastructure.rest.dto.response;

import java.math.BigDecimal;

public record OrderItemResponseDTO(Long id, Long productId, String productName, BigDecimal unitPrice, int quantity,
                                   BigDecimal lineTotal) {
}
