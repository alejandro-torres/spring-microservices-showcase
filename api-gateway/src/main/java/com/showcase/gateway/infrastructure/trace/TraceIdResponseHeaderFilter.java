package com.showcase.gateway.infrastructure.trace;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Devuelve el traceId en la cabecera {@code X-Trace-Id} de todas las respuestas, también las de error (401, 429,
 * 503...). Micrometer Tracing ya genera el traceId o continúa el que llega en {@code traceparent}; este filtro
 * solo lo hace visible al cliente para que pueda buscar su petición en Zipkin o en los logs.
 */
@Component
public class TraceIdResponseHeaderFilter implements WebFilter, Ordered {

    public static final String HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> {
            ExchangeTraceIds.of(exchange).ifPresent(traceId ->
                    exchange.getResponse().getHeaders().set(HEADER, traceId));
            return Mono.empty();
        });
        return chain.filter(exchange);
    }

    /** Antes que Spring Security, para que también las respuestas 401 y 403 lleven la cabecera. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
