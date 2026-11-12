package com.showcase.gateway.infrastructure.error;

import com.showcase.common.domain.exception.ErrorCode;
import com.showcase.common.infrastructure.i18n.ErrorMessageResolver;
import com.showcase.common.infrastructure.rest.dto.response.ErrorResponseDTO;
import com.showcase.gateway.infrastructure.trace.ExchangeTraceIds;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes the gateway's errors (401, 403, 404, 429, 503...) with the same {@code ErrorResponseDTO} and the same
 * English and Spanish messages as the services, so the client receives a single format wherever the error
 * comes from.
 */
@Component
@RequiredArgsConstructor
public class GatewayErrorWriter {

    static final Locale SPANISH = Locale.forLanguageTag("es");

    private final ErrorMessageResolver messageResolver;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, ErrorCode errorCode, Object... args) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }
        ErrorResponseDTO body = new ErrorResponseDTO(
                Instant.now(clock),
                status.value(),
                status.getReasonPhrase(),
                errorCode.code(),
                messageResolver.resolve(errorCode, List.of(args), locale(exchange)),
                path(exchange),
                ExchangeTraceIds.of(exchange).orElse(null),
                List.of());
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = response.bufferFactory().wrap(jsonMapper.writeValueAsBytes(body));
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * Path the client called. If the request was forwarded inside the gateway (e.g. the circuit breaker's
     * {@code forward:/fallback/...}), the current path is internal, so the first URI stored by Spring Cloud Gateway is
     * used.
     */
    static String path(ServerWebExchange exchange) {
        Set<URI> originalUris = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
        if (originalUris != null && !originalUris.isEmpty()) {
            return originalUris.iterator().next().getRawPath();
        }
        return exchange.getRequest().getPath().value();
    }

    /** English or Spanish according to {@code Accept-Language}; English if missing or not supported. */
    static Locale locale(ServerWebExchange exchange) {
        List<Locale.LanguageRange> ranges;
        try {
            ranges = exchange.getRequest().getHeaders().getAcceptLanguage();
        } catch (IllegalArgumentException e) {
            return Locale.ENGLISH;
        }
        Locale best = Locale.lookup(ranges, List.of(Locale.ENGLISH, SPANISH));
        return best == null ? Locale.ENGLISH : best;
    }
}
