package com.showcase.order.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/** Parte de la respuesta de product-service que usa order-service; el resto de campos se ignora. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductClientResponseDTO(Long id, String name, BigDecimal price, int stock) {
}
