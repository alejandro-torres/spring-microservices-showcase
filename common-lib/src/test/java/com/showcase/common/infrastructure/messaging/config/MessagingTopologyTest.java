package com.showcase.common.infrastructure.messaging.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

class MessagingTopologyTest {

    private static final MessagingProperties.Consumer ORDER_CREATED = new MessagingProperties.Consumer(
            "product-service.order-created", "order.events", "order.created");

    @Nested
    @DisplayName("declarables")
    class DeclarablesTest {

        @Test
        @DisplayName("declares its own exchange and that of each consumed event, of type topic and durable")
        void declarables_shouldDeclareTopicExchanges() {
            // given
            var properties = new MessagingProperties("product-service", "product.events",
                    Map.of("order-created", ORDER_CREATED), null);

            // when
            Declarables declarables = MessagingTopology.declarables(properties);

            // then
            assertThat(declarables.getDeclarablesByType(TopicExchange.class))
                    .extracting(TopicExchange::getName)
                    .containsExactlyInAnyOrder("product.events", "order.events");
            assertThat(declarables.getDeclarablesByType(TopicExchange.class)).allMatch(TopicExchange::isDurable);
        }

        @Test
        @DisplayName("each queue is durable, sends its failed messages to the DLX and has its DLQ")
        void declarables_shouldDeclareQueueWithDeadLetterQueue() {
            // given
            var properties = new MessagingProperties("product-service", "product.events",
                    Map.of("order-created", ORDER_CREATED), null);

            // when
            Declarables declarables = MessagingTopology.declarables(properties);

            // then
            Queue queue = declarables.getDeclarablesByType(Queue.class).stream()
                    .filter(q -> q.getName().equals("product-service.order-created")).findFirst().orElseThrow();
            assertThat(queue.isDurable()).isTrue();
            assertThat(queue.getArguments())
                    .containsEntry("x-dead-letter-exchange", "product-service.dlx")
                    .containsEntry("x-dead-letter-routing-key", "product-service.order-created.dlq");
            assertThat(declarables.getDeclarablesByType(Queue.class)).extracting(Queue::getName)
                    .contains("product-service.order-created.dlq");
            assertThat(declarables.getDeclarablesByType(DirectExchange.class)).extracting(DirectExchange::getName)
                    .containsExactly("product-service.dlx");
            assertThat(declarables.getDeclarablesByType(Binding.class))
                    .anySatisfy(binding -> {
                        assertThat(binding.getDestination()).isEqualTo("product-service.order-created");
                        assertThat(binding.getExchange()).isEqualTo("order.events");
                        assertThat(binding.getRoutingKey()).isEqualTo("order.created");
                    })
                    .anySatisfy(binding -> {
                        assertThat(binding.getDestination()).isEqualTo("product-service.order-created.dlq");
                        assertThat(binding.getExchange()).isEqualTo("product-service.dlx");
                    });
        }

        @Test
        @DisplayName("a service that only publishes declares no queues or DLX")
        void declarables_withoutConsumers_shouldOnlyDeclareOwnExchange() {
            // given
            var properties = new MessagingProperties("auth-service", "auth.events", null, null);

            // when
            Declarables declarables = MessagingTopology.declarables(properties);

            // then
            assertThat(declarables.getDeclarables()).hasSize(1);
            assertThat(declarables.getDeclarablesByType(TopicExchange.class)).extracting(TopicExchange::getName)
                    .containsExactly("auth.events");
        }
    }

    @Nested
    @DisplayName("default values")
    class Defaults {

        @Test
        @DisplayName("the outbox sends batches of 100, waits 5 s for the confirm and sets aside an unrouted event after 5 attempts")
        void outbox_withoutSettings_shouldUseDefaults() {
            // when
            var properties = new MessagingProperties("order-service", "order.events", null, null);

            // then
            assertThat(properties.outbox().batchSize()).isEqualTo(100);
            assertThat(properties.outbox().confirmTimeout()).hasSeconds(5);
            assertThat(properties.outbox().maxUnroutedAttempts()).isEqualTo(5);
            assertThat(properties.outbox().enabled()).isTrue();
            assertThat(properties.deadLetterExchange()).isEqualTo("order-service.dlx");
        }
    }
}
