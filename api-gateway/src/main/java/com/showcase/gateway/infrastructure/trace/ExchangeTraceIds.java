package com.showcase.gateway.infrastructure.trace;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.handler.TracingObservationHandler;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.web.server.ServerWebExchange;

/**
 * traceId de la petición en curso. En WebFlux no se puede confiar en un ThreadLocal (la petición cambia de hilo),
 * así que se lee del contexto de la observación HTTP, que Spring guarda en los atributos del exchange. Es el mismo
 * traceId que llega a los servicios en la cabecera {@code traceparent} y el que se busca en Zipkin.
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
