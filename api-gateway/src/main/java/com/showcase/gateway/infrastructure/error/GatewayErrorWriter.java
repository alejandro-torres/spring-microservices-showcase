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
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * Escribe los errores del gateway (401, 403, 404, 429, 503...) con el mismo {@code ErrorResponseDTO} y los mismos
 * mensajes en español e inglés que los servicios, para que el cliente reciba un único formato venga de donde venga
 * el error.
 */
@Component
public class GatewayErrorWriter {

    static final Locale SPANISH = Locale.forLanguageTag("es");

    private final ErrorMessageResolver messageResolver;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public GatewayErrorWriter(ErrorMessageResolver messageResolver, JsonMapper jsonMapper, Clock clock) {
        this.messageResolver = messageResolver;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

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
     * Ruta que llamó el cliente. Si la petición se reenvió dentro del gateway (p. ej. el {@code forward:/fallback/...}
     * del circuit breaker), la ruta actual es interna, así que se usa la primera URI que guarda Spring Cloud Gateway.
     */
    static String path(ServerWebExchange exchange) {
        Set<URI> originalUris = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
        if (originalUris != null && !originalUris.isEmpty()) {
            return originalUris.iterator().next().getRawPath();
        }
        return exchange.getRequest().getPath().value();
    }

    /** Español o inglés según {@code Accept-Language}; español si no llega o no está soportado. */
    static Locale locale(ServerWebExchange exchange) {
        List<Locale.LanguageRange> ranges;
        try {
            ranges = exchange.getRequest().getHeaders().getAcceptLanguage();
        } catch (IllegalArgumentException e) {
            return SPANISH;
        }
        Locale best = Locale.lookup(ranges, List.of(SPANISH, Locale.ENGLISH));
        return best == null ? SPANISH : best;
    }
}
