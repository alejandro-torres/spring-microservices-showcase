package com.showcase.gateway.infrastructure.fallback;

import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.gateway.infrastructure.error.GatewayErrorWriter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Circuit breaker response of each route when the service is down, too slow or has the circuit
 * open: 503 in the common format, without waiting for the timeout while the circuit stays open.
 */
@RestController
@RequiredArgsConstructor
public class FallbackController {

    private static final Logger log = LoggerFactory.getLogger(FallbackController.class);

    private final GatewayErrorWriter errorWriter;

    @RequestMapping("/fallback/{service}")
    public Mono<Void> fallback(@PathVariable String service, ServerWebExchange exchange) {
        Throwable cause = exchange.getAttribute(ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);
        log.warn("{} unavailable ({}): responding 503", service, cause == null ? "circuit open" : cause);
        return errorWriter.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, CommonErrorCode.SERVICE_UNAVAILABLE);
    }
}
