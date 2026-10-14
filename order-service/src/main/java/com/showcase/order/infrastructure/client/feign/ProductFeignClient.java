package com.showcase.order.infrastructure.client.feign;

import com.showcase.order.infrastructure.client.dto.ProductClientResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "product-service", url = "${showcase.clients.product-service.url:}")
public interface ProductFeignClient {

    @GetMapping("/api/v1/products/{id}")
    ProductClientResponseDTO findById(@PathVariable("id") Long id);
}
