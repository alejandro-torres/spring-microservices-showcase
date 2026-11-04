package com.showcase.gateway.infrastructure.trace;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Returns the traceId in the {@code X-Trace-Id} header of every response, including errors (401, 429,
 * 503...). Micrometer Tracing already generates the traceId or continues the one arriving in {@code traceparent};
 * this filter only makes it visible to the client so they can look up their request in Zipkin or the logs.
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

    /** Before Spring Security, so that 401 and 403 responses also carry the header. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
