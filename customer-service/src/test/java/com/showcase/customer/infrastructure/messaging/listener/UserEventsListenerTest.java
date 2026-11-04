package com.showcase.customer.infrastructure.messaging.listener;

import static org.mockito.Mockito.verify;

import com.showcase.customer.application.service.CustomerAccountService;
import com.showcase.events.EventEnvelopeDTO;
import com.showcase.events.auth.AccountLockedMessageDTO;
import com.showcase.events.auth.UserDisabledMessageDTO;
import com.showcase.events.auth.UserRegisteredMessageDTO;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserEventsListenerTest {

    private static final UUID EVENT_ID = UUID.randomUUID();

    @Mock
    private CustomerAccountService accountService;

    @InjectMocks
    private UserEventsListener listener;

    @Nested
    @DisplayName("auth-service messages")
    class Messages {

        @Test
        @DisplayName("user.registered creates the customer with the user's data")
        void onUserRegistered_shouldCreateCustomer() {
            listener.onUserRegistered(envelope("user.registered",
                    new UserRegisteredMessageDTO(10L, "ana", "ana@example.com", "Ana", "García")));

            verify(accountService).createForUser(EVENT_ID, 10L, "ana@example.com", "Ana", "García");
        }

        @Test
        @DisplayName("user.disabled deactivates the customer")
        void onUserDisabled_shouldDeactivate() {
            listener.onUserDisabled(envelope("user.disabled", new UserDisabledMessageDTO(10L, "ana")));

            verify(accountService).deactivateForUser(EVENT_ID, 10L);
        }

        @Test
        @DisplayName("account.locked records it with the unlock date")
        void onAccountLocked_shouldRecord() {
            Instant until = Instant.parse("2026-10-14T10:15:00Z");

            listener.onAccountLocked(envelope("account.locked", new AccountLockedMessageDTO(10L, "ana", until)));

            verify(accountService).recordAccountLocked(EVENT_ID, 10L, until);
        }
    }

    private static <T> EventEnvelopeDTO<T> envelope(String type, T payload) {
        return new EventEnvelopeDTO<>(EVENT_ID, type, 1, Instant.now(), "auth-service", payload);
    }
}
