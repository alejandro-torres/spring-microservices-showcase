package com.showcase.common.infrastructure.messaging.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Names of a service's RabbitMQ topology, in {@code application.yml} rather than scattered through the
 * code. Example:
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
 * @param source    name of the service, which goes in the envelope's {@code source} field and names its DLX
 * @param exchange  {@code topic} exchange where the service publishes (null if it does not publish)
 * @param consumers queues the service consumes, by logical name
 * @param outbox    settings of the outbox publisher
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

    /** The service's dead-letter exchange: each queue sends it the rejected messages. */
    public String deadLetterExchange() {
        return source + ".dlx";
    }

    public static String deadLetterQueue(String queue) {
        return queue + ".dlq";
    }

    /**
     * @param queue      durable queue in the format {@code <consumer-service>.<event>}
     * @param exchange   exchange of the service that publishes the event
     * @param routingKey routing key of the event ({@code <entity>.<action>})
     */
    public record Consumer(String queue, String exchange, String routingKey) {
    }

    /**
     * @param batchSize            events per publisher run
     * @param confirmTimeout       maximum wait for the publisher confirm of each message
     * @param maxUnroutedAttempts  attempts before giving up on an event that reaches no queue
     * @param enabled              allows disabling the publisher (e.g. in some tests)
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
