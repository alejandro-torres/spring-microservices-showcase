package com.showcase.product.infrastructure.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequestDTO(
        @NotBlank @Size(min = 2, max = 60) String name,
        @Size(max = 255) String description) {
}
