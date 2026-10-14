package com.showcase.common.infrastructure.messaging.outbox;

import java.util.function.Supplier;

/**
 * Mantiene la traza a través del outbox. El evento se guarda dentro de la petición que lo origina, pero se envía
 * después desde una tarea programada, que tiene su propia traza: sin esto, Zipkin no uniría la petición con el
 * envío a RabbitMQ ni con lo que hace después el consumidor.
 */
public interface OutboxTracing {

    /** Contexto de la traza actual en formato W3C {@code traceparent}, o null si no hay traza. */
    String currentTraceParent();

    /** Ejecuta el envío como hijo de la traza guardada con el evento (o sin traza si es null). */
    <T> T callWithParent(String traceParent, String spanName, Supplier<T> action);
}
