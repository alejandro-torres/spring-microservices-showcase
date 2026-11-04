package com.showcase.gateway.infrastructure.trace;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.handler.TracingObservationHandler;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.web.server.ServerWebExchange;

/**
 * traceId of the current request. In WebFlux a ThreadLocal cannot be trusted (the request changes threads),
 * so it is read from the HTTP observation's context, which Spring stores in the exchange's attributes. It is the
 * same traceId that reaches the services in the {@code traceparent} header and the one searched for in Zipkin.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExchangeTraceIds {

    public static Optional<String> of(ServerWebExchange exchange) {
        return ServerRequestObservationContext.findCurrent(exchange.getAttributes())
                .map(context -> context.<TracingObservationHandler.TracingContext>get(
                        TracingObservationHandler.TracingContext.class))
                .map(TracingObservationHandler.TracingContext::getSpan)
                .map(Span::context)
                .map(context -> context.traceId());
    }
}
