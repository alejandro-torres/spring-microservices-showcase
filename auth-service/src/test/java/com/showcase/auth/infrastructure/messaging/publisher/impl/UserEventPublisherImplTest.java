package com.showcase.auth.infrastructure.messaging.publisher.impl;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.showcase.auth.domain.event.AccountLockedEvent;
import com.showcase.auth.domain.event.UserDisabledEvent;
import com.showcase.auth.domain.event.UserRegisteredEvent;
import com.showcase.auth.infrastructure.messaging.mapper.UserMessageMapperImpl;
import com.showcase.common.infrastructure.messaging.outbox.OutboxEventWriter;
import com.showcase.events.auth.AccountLockedMessageDTO;
import com.showcase.events.auth.UserDisabledMessageDTO;
import com.showcase.events.auth.UserRegisteredMessageDTO;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserEventPublisherImplTest {

    private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");

    @Mock
    private OutboxEventWriter outbox;

    private UserEventPublisherImpl publisher;

    @BeforeEach
    void setUp() {
        publisher = new UserEventPublisherImpl(outbox, new UserMessageMapperImpl());
    }

    @Nested
    @DisplayName("publish")
    class Publish {

        @Test
        @DisplayName("guarda user.registered con el nombre para el perfil de cliente")
        void publish_registered_shouldAppend() {
            publisher.publish(new UserRegisteredEvent(10L, "ana", "ana@example.com", "Ana", "García", NOW));

            verify(outbox).append(eq("User"), eq(10L), eq("user.registered"),
                    eq(new UserRegisteredMessageDTO(10L, "ana", "ana@example.com", "Ana", "García")), eq(NOW));
        }

        @Test
        @DisplayName("guarda user.disabled")
        void publish_disabled_shouldAppend() {
            publisher.publish(new UserDisabledEvent(10L, "ana", NOW));

            verify(outbox).append(eq("User"), eq(10L), eq("user.disabled"),
                    eq(new UserDisabledMessageDTO(10L, "ana")), eq(NOW));
        }

        @Test
        @DisplayName("guarda account.locked con la fecha de desbloqueo")
        void publish_locked_shouldAppend() {
            Instant until = NOW.plusSeconds(900);

            publisher.publish(new AccountLockedEvent(10L, "ana", until, NOW));

            verify(outbox).append(eq("User"), eq(10L), eq("account.locked"),
                    eq(new AccountLockedMessageDTO(10L, "ana", until)), eq(NOW));
        }
    }
}
