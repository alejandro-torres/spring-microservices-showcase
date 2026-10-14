package com.showcase.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.showcase.events.order.OrderCreatedMessageDTO;
import com.showcase.events.order.OrderLineMessageDTO;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class EventEnvelopeDTOTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Nested
    @DisplayName("serialización JSON")
    class Json {

        @Test
        @DisplayName("el sobre tiene el formato del contrato, con la fecha en ISO-8601")
        void serialize_shouldWriteContractFormat() {
            // given
            UUID eventId = UUID.fromString("7d1f5b0e-4a64-4f4b-9b1e-2a6b1c2d3e4f");
            var envelope = new EventEnvelopeDTO<>(eventId, EventTypes.ORDER_CREATED, EventTypes.CURRENT_VERSION,
                    Instant.parse("2026-10-14T10:15:30Z"), "order-service",
                    new OrderCreatedMessageDTO(7L, 3L, List.of(new OrderLineMessageDTO(1L, 2))));

            // when
            JsonNode json = jsonMapper.readTree(jsonMapper.writeValueAsString(envelope));

            // then
            assertThat(json.get("eventId").asString()).isEqualTo(eventId.toString());
            assertThat(json.get("eventType").asString()).isEqualTo("order.created");
            assertThat(json.get("version").asInt()).isEqualTo(1);
            assertThat(json.get("occurredAt").asString()).isEqualTo("2026-10-14T10:15:30Z");
            assertThat(json.get("source").asString()).isEqualTo("order-service");
            assertThat(json.at("/payload/lines/0/productId").asLong()).isEqualTo(1L);
        }

        @Test
        @DisplayName("se lee de vuelta con el tipo del payload")
        void deserialize_withPayloadType_shouldRestoreEnvelope() {
            // given
            String json = """
                    {"eventId":"7d1f5b0e-4a64-4f4b-9b1e-2a6b1c2d3e4f","eventType":"order.created","version":1,
                     "occurredAt":"2026-10-14T10:15:30Z","source":"order-service",
                     "payload":{"orderId":7,"customerId":3,"lines":[{"productId":1,"quantity":2}]}}
                    """;

            // when
            EventEnvelopeDTO<OrderCreatedMessageDTO> envelope = jsonMapper.readValue(json, new TypeReference<>() {
            });

            // then
            assertThat(envelope.payload().orderId()).isEqualTo(7L);
            assertThat(envelope.payload().lines()).containsExactly(new OrderLineMessageDTO(1L, 2));
            assertThat(envelope.occurredAt()).isEqualTo(Instant.parse("2026-10-14T10:15:30Z"));
        }
    }
}
