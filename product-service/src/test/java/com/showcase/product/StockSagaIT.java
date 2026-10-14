package com.showcase.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.showcase.events.EventEnvelopeDTO;
import com.showcase.events.order.OrderCancelledMessageDTO;
import com.showcase.events.order.OrderCreatedMessageDTO;
import com.showcase.events.order.OrderLineMessageDTO;
import com.showcase.product.application.service.CategoryService;
import com.showcase.product.application.service.ProductService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lado de product-service de la saga por RabbitMQ real: este test hace de order-service enviando
 * {@code order.created} y {@code order.cancelled}, y escucha las respuestas en {@code product.events}.
 */
@SpringBootTest(properties = {
        "showcase.messaging.outbox.initial-delay=0s",
        "showcase.messaging.outbox.fixed-delay=200ms"})
@Import(TestcontainersConfiguration.class)
class StockSagaIT {

    private static final String PRODUCT_EVENTS_PROBE = "it.product-events";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final AtomicInteger ORDER_IDS = new AtomicInteger(1000);

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ProductService productService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private Long mouseId;
    private Long keyboardId;

    @BeforeEach
    void setUp() {
        amqpAdmin.declareQueue(new Queue(PRODUCT_EVENTS_PROBE, false));
        amqpAdmin.declareBinding(new Binding(PRODUCT_EVENTS_PROBE, Binding.DestinationType.QUEUE, "product.events",
                "stock.*", null));
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long categoryId = categoryService.create("Periféricos " + suffix, null).getId();
        mouseId = productService.create("Ratón " + suffix, null, new BigDecimal("12.50"), 5, categoryId).getId();
        keyboardId = productService.create("Teclado " + suffix, null, new BigDecimal("30.00"), 1, categoryId).getId();
    }

    @Nested
    @DisplayName("order.created")
    class OrderCreated {

        @Test
        @DisplayName("con stock suficiente reserva todas las líneas y responde stock.reserved")
        void orderCreated_withStock_shouldReserveAndReply() {
            // given
            long orderId = ORDER_IDS.incrementAndGet();

            // when
            sendFromOrderService("order.created", UUID.randomUUID(), new OrderCreatedMessageDTO(orderId, 3L,
                    List.of(new OrderLineMessageDTO(mouseId, 2), new OrderLineMessageDTO(keyboardId, 1))));

            // then
            JsonNode reply = nextReply(orderId);
            assertThat(reply.get("eventType").asString()).isEqualTo("stock.reserved");
            assertThat(reply.get("source").asString()).isEqualTo("product-service");
            assertThat(productService.findById(mouseId).getStock()).isEqualTo(3);
            assertThat(productService.findById(keyboardId).getStock()).isZero();
        }

        @Test
        @DisplayName("si falta stock en una línea no toca ninguna y responde stock.reservation-failed")
        void orderCreated_withoutStock_shouldReplyFailure() {
            // given
            long orderId = ORDER_IDS.incrementAndGet();

            // when
            sendFromOrderService("order.created", UUID.randomUUID(), new OrderCreatedMessageDTO(orderId, 3L,
                    List.of(new OrderLineMessageDTO(mouseId, 2), new OrderLineMessageDTO(keyboardId, 2))));

            // then
            JsonNode reply = nextReply(orderId);
            assertThat(reply.get("eventType").asString()).isEqualTo("stock.reservation-failed");
            assertThat(reply.at("/payload/reason").asString()).isEqualTo("INSUFFICIENT_STOCK");
            assertThat(reply.at("/payload/productId").asLong()).isEqualTo(keyboardId);
            assertThat(productService.findById(mouseId).getStock()).isEqualTo(5);
            assertThat(productService.findById(keyboardId).getStock()).isEqualTo(1);
        }

        @Test
        @DisplayName("un order.created duplicado no reserva dos veces")
        void orderCreated_duplicate_shouldReserveOnce() {
            // given
            long orderId = ORDER_IDS.incrementAndGet();
            UUID eventId = UUID.randomUUID();
            var message = new OrderCreatedMessageDTO(orderId, 3L, List.of(new OrderLineMessageDTO(mouseId, 2)));

            // when
            sendFromOrderService("order.created", eventId, message);
            sendFromOrderService("order.created", eventId, message);

            // then
            nextReply(orderId);
            await().during(Duration.ofSeconds(1)).atMost(TIMEOUT).untilAsserted(() ->
                    assertThat(productService.findById(mouseId).getStock()).isEqualTo(3));
        }
    }

    @Nested
    @DisplayName("order.cancelled")
    class OrderCancelled {

        @Test
        @DisplayName("devuelve el stock reservado (también de un producto ya borrado) y responde stock.released")
        void orderCancelled_shouldReleaseStock() {
            // given
            long orderId = ORDER_IDS.incrementAndGet();
            var lines = List.of(new OrderLineMessageDTO(mouseId, 2), new OrderLineMessageDTO(keyboardId, 1));
            sendFromOrderService("order.created", UUID.randomUUID(), new OrderCreatedMessageDTO(orderId, 3L, lines));
            nextReply(orderId);
            productService.delete(keyboardId);

            // when
            sendFromOrderService("order.cancelled", UUID.randomUUID(), new OrderCancelledMessageDTO(orderId, lines));

            // then
            JsonNode reply = nextReply(orderId);
            assertThat(reply.get("eventType").asString()).isEqualTo("stock.released");
            assertThat(productService.findById(mouseId).getStock()).isEqualTo(5);
        }
    }

    private void sendFromOrderService(String routingKey, UUID eventId, Object payload) {
        var envelope = new EventEnvelopeDTO<>(eventId, routingKey, 1, Instant.now(), "order-service", payload);
        Message message = MessageBuilder.withBody(jsonMapper.writeValueAsBytes(envelope))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(eventId.toString())
                .setType(routingKey)
                .build();
        rabbitTemplate.send("order.events", routingKey, message);
    }

    /** Siguiente respuesta de product-service sobre ese pedido (descarta las de otros tests). */
    private JsonNode nextReply(long orderId) {
        long deadline = System.currentTimeMillis() + TIMEOUT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            Message message = rabbitTemplate.receive(PRODUCT_EVENTS_PROBE, 1_000);
            if (message != null) {
                JsonNode event = jsonMapper.readTree(message.getBody());
                if (event.at("/payload/orderId").asLong() == orderId) {
                    return event;
                }
            }
        }
        throw new AssertionError("product-service no ha respondido al pedido " + orderId);
    }
}
