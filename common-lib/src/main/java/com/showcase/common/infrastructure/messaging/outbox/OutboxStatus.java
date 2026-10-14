package com.showcase.common.infrastructure.messaging.outbox;

/** Estado de un evento en la tabla {@code outbox_event}. */
public enum OutboxStatus {

    /** Pendiente de enviar (o de reintentar). */
    PENDING,
    /** RabbitMQ ha confirmado que lo ha recibido y que tenía una cola de destino. */
    SENT,
    /**
     * Ninguna cola recibía el evento después de varios intentos. Se deja en la tabla para revisarlo; para
     * reenviarlo basta con volver a ponerlo en PENDING.
     */
    UNROUTED
}
