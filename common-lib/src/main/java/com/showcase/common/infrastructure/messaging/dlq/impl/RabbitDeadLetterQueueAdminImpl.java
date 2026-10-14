package com.showcase.common.infrastructure.messaging.dlq.impl;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.GetResponse;
import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.ExternalServiceException;
import com.showcase.common.domain.exception.ResourceNotFoundException;
import com.showcase.common.infrastructure.messaging.config.MessagingProperties;
import com.showcase.common.infrastructure.messaging.dlq.DeadLetterQueueAdmin;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.support.DefaultMessagePropertiesConverter;

/**
 * Implementación con el canal de RabbitMQ:
 * <ul>
 *   <li>Consultar: {@code basic.get} sin ACK de varios mensajes y al final un {@code basic.nack} con requeue,
 *       que los devuelve a la DLQ tal cual.</li>
 *   <li>Reenviar: cada mensaje se publica en su cola original (exchange por defecto) esperando el publisher
 *       confirm, y solo entonces se hace ACK en la DLQ. Si el reenvío falla, el mensaje sigue en la DLQ.</li>
 * </ul>
 */
public class RabbitDeadLetterQueueAdminImpl implements DeadLetterQueueAdmin {

    private static final Logger log = LoggerFactory.getLogger(RabbitDeadLetterQueueAdminImpl.class);
    private static final long CONFIRM_TIMEOUT_MS = 5_000;

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties properties;
    private final DefaultMessagePropertiesConverter propertiesConverter = new DefaultMessagePropertiesConverter();

    public RabbitDeadLetterQueueAdminImpl(RabbitTemplate rabbitTemplate, MessagingProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Override
    public List<DeadLetterQueue> queues() {
        return properties.consumers().values().stream()
                .map(consumer -> {
                    String name = MessagingProperties.deadLetterQueue(consumer.queue());
                    Long count = rabbitTemplate.execute(channel -> channel.messageCount(name));
                    return new DeadLetterQueue(name, consumer.queue(), count == null ? 0 : count);
                })
                .toList();
    }

    @Override
    public List<DeadLetterMessage> peek(String deadLetterQueue, int limit) {
        requireKnown(deadLetterQueue);
        return rabbitTemplate.execute(channel -> {
            List<DeadLetterMessage> messages = new ArrayList<>();
            long lastTag = -1;
            for (int i = 0; i < limit; i++) {
                GetResponse response = channel.basicGet(deadLetterQueue, false);
                if (response == null) {
                    break;
                }
                lastTag = response.getEnvelope().getDeliveryTag();
                messages.add(toDeadLetterMessage(response));
            }
            if (lastTag >= 0) {
                channel.basicNack(lastTag, true, true);
            }
            return messages;
        });
    }

    @Override
    public int replay(String deadLetterQueue, int max) {
        String sourceQueue = requireKnown(deadLetterQueue);
        // El callback no lanza la excepción de dominio: RabbitTemplate la envolvería en una de AMQP.
        ReplayOutcome outcome = rabbitTemplate.execute(channel -> {
            int count = 0;
            for (int i = 0; i < max; i++) {
                GetResponse response = channel.basicGet(deadLetterQueue, false);
                if (response == null) {
                    break;
                }
                long tag = response.getEnvelope().getDeliveryTag();
                if (!republish(response, sourceQueue)) {
                    channel.basicNack(tag, false, true);
                    return new ReplayOutcome(count, true);
                }
                channel.basicAck(tag, false);
                count++;
            }
            return new ReplayOutcome(count, false);
        });
        int total = outcome == null ? 0 : outcome.replayed();
        log.info("DLQ: {} mensajes reenviados de {} a {}", total, deadLetterQueue, sourceQueue);
        if (outcome != null && outcome.failed()) {
            throw new ExternalServiceException(CommonErrorCode.SERVICE_UNAVAILABLE);
        }
        return total;
    }

    private boolean republish(GetResponse response, String sourceQueue) {
        MessageProperties messageProperties = propertiesConverter.toMessageProperties(response.getProps(),
                response.getEnvelope(), StandardCharsets.UTF_8.name());
        Message message = new Message(response.getBody(), messageProperties);
        CorrelationData correlation = new CorrelationData(messageProperties.getMessageId());
        try {
            rabbitTemplate.send("", sourceQueue, message, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(CONFIRM_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            return confirm.ack() && correlation.getReturned() == null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.warn("DLQ: no se ha podido reenviar el mensaje {} a {}", messageProperties.getMessageId(),
                    sourceQueue, e);
            return false;
        }
    }

    /** Devuelve la cola original de la DLQ, o 404 si la DLQ no es de este servicio. */
    private String requireKnown(String deadLetterQueue) {
        return properties.consumers().values().stream()
                .map(MessagingProperties.Consumer::queue)
                .filter(queue -> MessagingProperties.deadLetterQueue(queue).equals(deadLetterQueue))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(CommonErrorCode.RESOURCE_NOT_FOUND,
                        "dead-letter queue", deadLetterQueue));
    }

    private record ReplayOutcome(int replayed, boolean failed) {
    }

    @SuppressWarnings("unchecked")
    private static DeadLetterMessage toDeadLetterMessage(GetResponse response) {
        AMQP.BasicProperties props = response.getProps();
        String reason = null;
        long deathCount = 0;
        Map<String, Object> headers = props.getHeaders();
        if (headers != null && headers.get("x-death") instanceof List<?> deaths && !deaths.isEmpty()
                && deaths.getFirst() instanceof Map<?, ?> death) {
            Map<String, Object> first = (Map<String, Object>) death;
            reason = String.valueOf(first.get("reason"));
            if (first.get("count") instanceof Number count) {
                deathCount = count.longValue();
            }
        }
        return new DeadLetterMessage(props.getMessageId(), props.getType(), reason, deathCount,
                new String(response.getBody(), StandardCharsets.UTF_8));
    }
}
