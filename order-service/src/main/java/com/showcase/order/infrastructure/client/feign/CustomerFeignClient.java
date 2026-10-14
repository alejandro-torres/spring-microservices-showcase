package com.showcase.order.infrastructure.client.feign;

import com.showcase.order.infrastructure.client.dto.CustomerClientResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "customer-service", url = "${showcase.clients.customer-service.url}")
public interface CustomerFeignClient {

    @GetMapping("/api/v1/customers/{id}")
    CustomerClientResponseDTO findById(@PathVariable("id") Long id);
}
