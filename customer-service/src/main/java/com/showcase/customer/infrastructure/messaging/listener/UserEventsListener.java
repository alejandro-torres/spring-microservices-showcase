package com.showcase.customer.infrastructure.messaging.listener;

import com.showcase.common.infrastructure.messaging.error.LoggingRabbitListenerErrorHandler;
import com.showcase.customer.application.service.CustomerAccountService;
import com.showcase.events.EventEnvelopeDTO;
import com.showcase.events.auth.AccountLockedMessageDTO;
import com.showcase.events.auth.UserDisabledMessageDTO;
import com.showcase.events.auth.UserRegisteredMessageDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Inbound adapter for auth-service events: it converts the message and calls
 * {@link CustomerAccountService}, with no business logic or transaction of its own.
 */
@Component
@RequiredArgsConstructor
public class UserEventsListener {

    private final CustomerAccountService accountService;

    @RabbitListener(queues = "${showcase.messaging.consumers.user-registered.queue}",
            errorHandler = LoggingRabbitListenerErrorHandler.BEAN_NAME)
    public void onUserRegistered(EventEnvelopeDTO<UserRegisteredMessageDTO> envelope) {
        UserRegisteredMessageDTO user = envelope.payload();
        accountService.createForUser(envelope.eventId(), user.userId(), user.email(), user.firstName(),
                user.lastName());
    }

    @RabbitListener(queues = "${showcase.messaging.consumers.user-disabled.queue}",
            errorHandler = LoggingRabbitListenerErrorHandler.BEAN_NAME)
    public void onUserDisabled(EventEnvelopeDTO<UserDisabledMessageDTO> envelope) {
        accountService.deactivateForUser(envelope.eventId(), envelope.payload().userId());
    }

    @RabbitListener(queues = "${showcase.messaging.consumers.account-locked.queue}",
            errorHandler = LoggingRabbitListenerErrorHandler.BEAN_NAME)
    public void onAccountLocked(EventEnvelopeDTO<AccountLockedMessageDTO> envelope) {
        AccountLockedMessageDTO account = envelope.payload();
        accountService.recordAccountLocked(envelope.eventId(), account.userId(), account.lockedUntil());
    }
}
