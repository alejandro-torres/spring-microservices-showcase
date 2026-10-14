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
        @DisplayName("declara el exchange propio y el de cada evento consumido, de tipo topic y duraderos")
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
        @DisplayName("cada cola es duradera, manda sus mensajes fallidos al DLX y tiene su DLQ")
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
        @DisplayName("un servicio que solo publica no declara colas ni DLX")
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
    @DisplayName("valores por defecto")
    class Defaults {

        @Test
        @DisplayName("el outbox envía lotes de 100, espera 5 s el confirm y aparta un evento sin destino tras 5 intentos")
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
