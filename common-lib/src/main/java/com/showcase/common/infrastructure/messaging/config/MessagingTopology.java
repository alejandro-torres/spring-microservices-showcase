package com.showcase.common.infrastructure.messaging.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;

/**
 * Builds a service's exchanges, queues and bindings from {@link MessagingProperties}. RabbitAdmin declares
 * them on connecting; declaring something that already exists with the same arguments does nothing, which is why
 * the consumer also declares the exchange it reads from (so it does not depend on the startup order).
 * <p>
 * Each queue rejects its failed messages to the service's DLX ({@code <service>.dlx}, of type direct), which
 * leaves them in its DLQ ({@code <queue>.dlq}).
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MessagingTopology {

    public static Declarables declarables(MessagingProperties properties) {
        List<Declarable> declarables = new ArrayList<>();
        Set<String> exchanges = new LinkedHashSet<>();
        if (properties.exchange() != null) {
            exchanges.add(properties.exchange());
        }
        properties.consumers().values().forEach(consumer -> exchanges.add(consumer.exchange()));
        exchanges.forEach(name -> declarables.add(new TopicExchange(name, true, false)));

        if (!properties.consumers().isEmpty()) {
            DirectExchange deadLetterExchange = new DirectExchange(properties.deadLetterExchange(), true, false);
            declarables.add(deadLetterExchange);
            for (MessagingProperties.Consumer consumer : properties.consumers().values()) {
                String deadLetterQueueName = MessagingProperties.deadLetterQueue(consumer.queue());
                Queue queue = QueueBuilder.durable(consumer.queue())
                        .deadLetterExchange(deadLetterExchange.getName())
                        .deadLetterRoutingKey(deadLetterQueueName)
                        .build();
                Queue deadLetterQueue = QueueBuilder.durable(deadLetterQueueName).build();
                declarables.add(queue);
                declarables.add(deadLetterQueue);
                declarables.add(binding(queue, consumer.exchange(), consumer.routingKey()));
                declarables.add(BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(deadLetterQueueName));
            }
        }
        return new Declarables(declarables);
    }

    private static Binding binding(Queue queue, String exchange, String routingKey) {
        return new Binding(queue.getName(), Binding.DestinationType.QUEUE, exchange, routingKey, null);
    }
}
