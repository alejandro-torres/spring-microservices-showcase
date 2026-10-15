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
 * Respuesta del circuit breaker de cada ruta cuando el servicio está caído, tarda demasiado o tiene el circuito
 * abierto: 503 en el formato común, sin esperar al timeout mientras el circuito siga abierto.
 */
@RestController
@RequiredArgsConstructor
public class FallbackController {

    private static final Logger log = LoggerFactory.getLogger(FallbackController.class);

    private final GatewayErrorWriter errorWriter;

    @RequestMapping("/fallback/{service}")
    public Mono<Void> fallback(@PathVariable String service, ServerWebExchange exchange) {
        Throwable cause = exchange.getAttribute(ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);
        log.warn("{} no disponible ({}): se responde 503", service, cause == null ? "circuito abierto" : cause);
        return errorWriter.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, CommonErrorCode.SERVICE_UNAVAILABLE);
    }
}
