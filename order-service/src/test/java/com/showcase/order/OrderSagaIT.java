package com.showcase.order;

import static com.showcase.order.testdata.OrderMother.ADDRESS;
import static com.showcase.order.testdata.OrderMother.item;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.showcase.events.EventEnvelopeDTO;
import com.showcase.events.product.StockReservationFailedMessageDTO;
import com.showcase.events.product.StockReservedMessageDTO;
import com.showcase.order.application.service.OrderService;
import com.showcase.order.application.service.OrderWriter;
import com.showcase.order.domain.model.Order;
import com.showcase.order.domain.model.OrderStatus;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The saga through real RabbitMQ: the outbox publishes {@code order.created} and {@code order.cancelled}, and this
 * test plays product-service, sending the responses to the {@code product.events} exchange.
 */
@SpringBootTest(properties = {
        "showcase.messaging.outbox.initial-delay=0s",
        "showcase.messaging.outbox.fixed-delay=200ms"})
@Import(TestcontainersConfiguration.class)
class OrderSagaIT {

    /** Test queue that receives everything order-service publishes. */
    private static final String ORDER_EVENTS_PROBE = "it.order-events";
    private static final String STOCK_RESERVED_DLQ = "order-service.stock-reserved.dlq";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @Autowired
    private OrderWriter orderWriter;

    @Autowired
    private OrderService orderService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        amqpAdmin.declareQueue(new Queue(ORDER_EVENTS_PROBE, false));
        amqpAdmin.declareBinding(new Binding(ORDER_EVENTS_PROBE, Binding.DestinationType.QUEUE, "order.events",
                "order.*", null));
        amqpAdmin.purgeQueue(ORDER_EVENTS_PROBE, false);
        amqpAdmin.purgeQueue(STOCK_RESERVED_DLQ, false);
    }

    @Nested
    @DisplayName("order creation")
    class Creation {

        @Test
        @DisplayName("publishes order.created with the lines and confirms it on receiving stock.reserved")
        void createdOrder_whenStockReserved_shouldBeConfirmed() {
            // given
            Order order = createPendingOrder();

            // when
            JsonNode created = nextOrderEvent("order.created", order.getId());

            // then
            assertThat(created.at("/payload/orderId").asLong()).isEqualTo(order.getId());
            assertThat(created.at("/payload/lines/0/productId").asLong()).isEqualTo(10L);
            assertThat(created.at("/payload/lines/0/quantity").asInt()).isEqualTo(2);
            assertThat(created.get("source").asString()).isEqualTo("order-service");

            // when
            sendFromProductService("stock.reserved", UUID.randomUUID(), new StockReservedMessageDTO(order.getId()));

            // then
            awaitStatus(order.getId(), OrderStatus.CONFIRMED);
        }

        @Test
        @DisplayName("rejects it with the reason on receiving stock.reservation-failed")
        void createdOrder_whenReservationFails_shouldBeRejected() {
            // given
            Order order = createPendingOrder();

            // when
            sendFromProductService("stock.reservation-failed", UUID.randomUUID(),
                    new StockReservationFailedMessageDTO(order.getId(), "INSUFFICIENT_STOCK", 10L));

            // then
            awaitStatus(order.getId(), OrderStatus.REJECTED);
            assertThat(orderService.findById(order.getId()).getRejectionReason()).isEqualTo("INSUFFICIENT_STOCK");
        }
    }

    @Nested
    @DisplayName("reliability")
    class Reliability {

        @Test
        @DisplayName("a duplicate message is processed only once")
        void duplicateMessage_shouldBeProcessedOnce() {
            // given
            Order order = createPendingOrder();
            UUID eventId = UUID.randomUUID();
            sendFromProductService("stock.reserved", eventId, new StockReservedMessageDTO(order.getId()));
            awaitStatus(order.getId(), OrderStatus.CONFIRMED);

            // when: RabbitMQ delivers "at least once", so the same event can arrive again
            sendFromProductService("stock.reserved", eventId, new StockReservedMessageDTO(order.getId()));

            // then: it neither fails nor goes to the DLQ (confirming a CONFIRMED order again would fail)
            assertThat(rabbitTemplate.receive(STOCK_RESERVED_DLQ, 2_000)).isNull();
            assertThat(jdbcClient.sql("SELECT count(*) FROM processed_event WHERE event_id = :id")
                    .param("id", eventId).query(Long.class).single()).isEqualTo(1);
        }

        @Test
        @DisplayName("a response for an order that does not exist goes to the DLQ without retries")
        void unknownOrder_shouldGoToDeadLetterQueue() {
            // when
            UUID eventId = UUID.randomUUID();
            sendFromProductService("stock.reserved", eventId, new StockReservedMessageDTO(999_999L));

            // then: without retries (1 s + 2 s + 4 s) it arrives immediately
            Message dead = rabbitTemplate.receive(STOCK_RESERVED_DLQ, 3_000);
            assertThat(dead).isNotNull();
            assertThat(dead.getMessageProperties().getMessageId()).isEqualTo(eventId.toString());
        }

        @Test
        @DisplayName("a message with malformed JSON goes to the DLQ")
        void malformedMessage_shouldGoToDeadLetterQueue() {
            // when
            Message message = MessageBuilder.withBody("{not json".getBytes(StandardCharsets.UTF_8))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON).setMessageId("broken-1").build();
            rabbitTemplate.send("product.events", "stock.reserved", message);

            // then
            Message dead = rabbitTemplate.receive(STOCK_RESERVED_DLQ, 3_000);
            assertThat(dead).isNotNull();
            assertThat(dead.getMessageProperties().getMessageId()).isEqualTo("broken-1");
        }
    }

    @Nested
    @DisplayName("cancellation")
    class Cancellation {

        @Test
        @DisplayName("cancelling a confirmed order publishes order.cancelled to return the stock")
        void cancelConfirmedOrder_shouldPublishOrderCancelled() {
            // given
            Order order = createPendingOrder();
            nextOrderEvent("order.created", order.getId());
            sendFromProductService("stock.reserved", UUID.randomUUID(), new StockReservedMessageDTO(order.getId()));
            awaitStatus(order.getId(), OrderStatus.CONFIRMED);

            // when
            orderService.cancel(order.getId());

            // then
            JsonNode cancelled = nextOrderEvent("order.cancelled", order.getId());
            assertThat(cancelled.at("/payload/orderId").asLong()).isEqualTo(order.getId());
            assertThat(cancelled.at("/payload/lines/1/productId").asLong()).isEqualTo(11L);
        }
    }

    private Order createPendingOrder() {
        return orderWriter.create(Order.create(3L, ADDRESS, List.of(item(10, "5", 2), item(11, "20", 1))));
    }

    private void sendFromProductService(String routingKey, UUID eventId, Object payload) {
        var envelope = new EventEnvelopeDTO<>(eventId, routingKey, 1, Instant.now(), "product-service", payload);
        Message message = MessageBuilder.withBody(jsonMapper.writeValueAsBytes(envelope))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(eventId.toString())
                .setType(routingKey)
                .build();
        rabbitTemplate.send("product.events", routingKey, message);
    }

    /** Next event of the given type that order-service publishes for that order (discards the others). */
    private JsonNode nextOrderEvent(String eventType, Long orderId) {
        long deadline = System.currentTimeMillis() + TIMEOUT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            Message message = rabbitTemplate.receive(ORDER_EVENTS_PROBE, 1_000);
            if (message != null && eventType.equals(message.getMessageProperties().getType())) {
                JsonNode event = jsonMapper.readTree(message.getBody());
                if (event.at("/payload/orderId").asLong() == orderId) {
                    return event;
                }
            }
        }
        throw new AssertionError("No event was published: " + eventType);
    }

    private void awaitStatus(Long orderId, OrderStatus expected) {
        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(orderService.findById(orderId).getStatus()).isEqualTo(expected));
    }
}
