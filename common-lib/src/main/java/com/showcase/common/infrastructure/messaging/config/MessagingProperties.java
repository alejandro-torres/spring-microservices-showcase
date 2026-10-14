package com.showcase.common.infrastructure.messaging.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nombres de la topología de RabbitMQ de un servicio, en {@code application.yml} en lugar de repartidos por el
 * código. Ejemplo:
 * <pre>
 * showcase:
 *   messaging:
 *     source: order-service
 *     exchange: order.events
 *     consumers:
 *       stock-reserved:
 *         queue: order-service.stock-reserved
 *         exchange: product.events
 *         routing-key: stock.reserved
 * </pre>
 *
 * @param source    nombre del servicio, que va en el campo {@code source} del sobre y da nombre a su DLX
 * @param exchange  exchange {@code topic} donde publica el servicio (null si no publica)
 * @param consumers colas que consume el servicio, por nombre lógico
 * @param outbox    ajustes del publicador del outbox
 */
@ConfigurationProperties(prefix = "showcase.messaging")
public record MessagingProperties(
        String source,
        String exchange,
        Map<String, Consumer> consumers,
        Outbox outbox) {

    public MessagingProperties {
        consumers = consumers == null ? Map.of() : new LinkedHashMap<>(consumers);
        outbox = outbox == null ? new Outbox(null, null, null, null) : outbox;
    }

    /** Exchange de mensajes muertos del servicio: cada cola le manda los mensajes rechazados. */
    public String deadLetterExchange() {
        return source + ".dlx";
    }

    public static String deadLetterQueue(String queue) {
        return queue + ".dlq";
    }

    /**
     * @param queue      cola duradera con el formato {@code <servicio-consumidor>.<evento>}
     * @param exchange   exchange del servicio que publica el evento
     * @param routingKey routing key del evento ({@code <entidad>.<acción>})
     */
    public record Consumer(String queue, String exchange, String routingKey) {
    }

    /**
     * @param batchSize            eventos por ejecución del publicador
     * @param confirmTimeout       espera máxima del publisher confirm de cada mensaje
     * @param maxUnroutedAttempts  intentos antes de dar por imposible un evento que no llega a ninguna cola
     * @param enabled              permite desactivar el publicador (p. ej. en algunos tests)
     */
    public record Outbox(Integer batchSize, Duration confirmTimeout, Integer maxUnroutedAttempts, Boolean enabled) {

        public Outbox {
            batchSize = batchSize == null ? 100 : batchSize;
            confirmTimeout = confirmTimeout == null ? Duration.ofSeconds(5) : confirmTimeout;
            maxUnroutedAttempts = maxUnroutedAttempts == null ? 5 : maxUnroutedAttempts;
            enabled = enabled == null || enabled;
        }
    }
}
