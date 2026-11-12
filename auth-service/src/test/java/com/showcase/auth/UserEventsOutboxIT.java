package com.showcase.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.showcase.auth.application.service.UserService;
import com.showcase.auth.domain.exception.UsernameAlreadyExistsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * User events are stored in the outbox in the same transaction as the change. The publisher is
 * disabled to inspect the rows before they are sent.
 */
@SpringBootTest(properties = "showcase.messaging.outbox.enabled=false")
@Import(TestcontainersConfiguration.class)
class UserEventsOutboxIT {

    private static final String PASSWORD = "Secret-Test-123";

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private JsonMapper jsonMapper;

    @Nested
    @DisplayName("registration")
    class Registration {

        @Test
        @DisplayName("saves user.registered with the common envelope and the customer's name")
        void register_shouldStoreUserRegisteredEvent() {
            // when
            long userId = userService.register("outbox.ana", "outbox.ana@example.com", PASSWORD, "Ana", "García")
                    .getId();

            // then
            JsonNode envelope = jsonMapper.readTree(payloadOf("user.registered", userId));
            assertThat(envelope.get("source").asString()).isEqualTo("auth-service");
            assertThat(envelope.get("version").asInt()).isEqualTo(1);
            assertThat(envelope.at("/payload/userId").asLong()).isEqualTo(userId);
            assertThat(envelope.at("/payload/firstName").asString()).isEqualTo("Ana");
            assertThat(envelope.at("/payload/email").asString()).isEqualTo("outbox.ana@example.com");
        }

        @Test
        @DisplayName("if the sign-up fails no event remains")
        void register_whenFails_shouldNotStoreEvent() {
            // given
            userService.register("outbox.dup", "outbox.dup@example.com", PASSWORD, "Dup", "Licate");
            long before = countEvents("user.registered");

            // when / then
            assertThatThrownBy(() -> userService.register("outbox.dup", "other@example.com", PASSWORD, "Other", "More"))
                    .isInstanceOf(UsernameAlreadyExistsException.class);
            assertThat(countEvents("user.registered")).isEqualTo(before);
        }
    }

    @Nested
    @DisplayName("deactivation")
    class Disabling {

        @Test
        @DisplayName("deleting a user saves user.disabled")
        void delete_shouldStoreUserDisabledEvent() {
            // given
            long userId = userService.register("outbox.bea", "outbox.bea@example.com", PASSWORD, "Bea", "López")
                    .getId();

            // when
            userService.delete(userId);

            // then
            JsonNode envelope = jsonMapper.readTree(payloadOf("user.disabled", userId));
            assertThat(envelope.at("/payload/username").asString()).isEqualTo("outbox.bea");
        }
    }

    private String payloadOf(String eventType, long userId) {
        return jdbcClient.sql("SELECT CAST(payload AS text) FROM outbox_event WHERE event_type = :type "
                        + "AND aggregate_id = :id")
                .param("type", eventType)
                .param("id", String.valueOf(userId))
                .query(String.class).single();
    }

    private long countEvents(String eventType) {
        return jdbcClient.sql("SELECT count(*) FROM outbox_event WHERE event_type = :type")
                .param("type", eventType).query(Long.class).single();
    }
}
