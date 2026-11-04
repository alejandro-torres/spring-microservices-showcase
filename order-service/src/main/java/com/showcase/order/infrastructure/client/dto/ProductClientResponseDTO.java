package com.showcase.order.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/** The part of product-service's response that order-service uses; the other fields are ignored. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductClientResponseDTO(Long id, String name, BigDecimal price, int stock) {
}
