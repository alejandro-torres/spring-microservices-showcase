package com.showcase.gateway.infrastructure.error;

import com.showcase.common.domain.exception.CommonErrorCode;
import java.net.ConnectException;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Convierte cualquier error del propio gateway al formato común, sin detalles internos:
 * <ul>
 *   <li>Límite de peticiones superado → 429 con {@code Retry-After} y las cabeceras {@code X-RateLimit-*}.</li>
 *   <li>Ninguna ruta coincide → 404.</li>
 *   <li>El servicio no tiene réplicas, no responde o tarda demasiado → 503.</li>
 *   <li>Cualquier otra cosa → 500, con el detalle solo en el log.</li>
 * </ul>
 * Order -2: antes que el handler por defecto de Spring Boot.
 */
@Component
@Order(-2)
@RequiredArgsConstructor
public class GatewayErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorWebExceptionHandler.class);

    private final GatewayErrorWriter errorWriter;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
        if (error instanceof HttpClientErrorException.TooManyRequests tooManyRequests) {
            return tooManyRequests(exchange, tooManyRequests);
        }
        if (error instanceof ResponseStatusException statusException) {
            return fromStatus(exchange, statusException.getStatusCode(), error);
        }
        if (isUnavailable(error)) {
            log.warn("Servicio no disponible para {}: {}", exchange.getRequest().getPath(), error.toString());
            return errorWriter.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, CommonErrorCode.SERVICE_UNAVAILABLE);
        }
        log.error("Error inesperado en el gateway en {}", exchange.getRequest().getPath(), error);
        return errorWriter.write(exchange, HttpStatus.INTERNAL_SERVER_ERROR, CommonErrorCode.INTERNAL_ERROR);
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange, HttpClientErrorException error) {
        HttpHeaders limitHeaders = error.getResponseHeaders() != null ? error.getResponseHeaders()
                : exchange.getResponse().getHeaders();
        exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER,
                String.valueOf(RetryAfter.seconds(limitHeaders)));
        return errorWriter.write(exchange, HttpStatus.TOO_MANY_REQUESTS, CommonErrorCode.RATE_LIMIT_EXCEEDED);
    }

    private Mono<Void> fromStatus(ServerWebExchange exchange, HttpStatusCode statusCode, Throwable error) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        if (status == HttpStatus.NOT_FOUND) {
            return errorWriter.write(exchange, status, CommonErrorCode.ENDPOINT_NOT_FOUND,
                    exchange.getRequest().getPath().value());
        }
        if (status == HttpStatus.METHOD_NOT_ALLOWED) {
            return errorWriter.write(exchange, status, CommonErrorCode.METHOD_NOT_ALLOWED,
                    exchange.getRequest().getMethod().name());
        }
        if (status == HttpStatus.SERVICE_UNAVAILABLE || status == HttpStatus.GATEWAY_TIMEOUT
                || status == HttpStatus.BAD_GATEWAY) {
            log.warn("Servicio no disponible para {}: {}", exchange.getRequest().getPath(), error.toString());
            return errorWriter.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, CommonErrorCode.SERVICE_UNAVAILABLE);
        }
        if (status != null && status.is4xxClientError()) {
            return errorWriter.write(exchange, HttpStatus.BAD_REQUEST, CommonErrorCode.MALFORMED_REQUEST);
        }
        log.error("Error inesperado en el gateway en {}", exchange.getRequest().getPath(), error);
        return errorWriter.write(exchange, HttpStatus.INTERNAL_SERVER_ERROR, CommonErrorCode.INTERNAL_ERROR);
    }

    private static boolean isUnavailable(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof ConnectException || current instanceof TimeoutException) {
                return true;
            }
        }
        return false;
    }
}
