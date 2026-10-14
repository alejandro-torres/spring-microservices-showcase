package com.showcase.customer.infrastructure.messaging.listener;

import com.showcase.common.infrastructure.messaging.error.LoggingRabbitListenerErrorHandler;
import com.showcase.customer.application.service.CustomerAccountService;
import com.showcase.events.EventEnvelopeDTO;
import com.showcase.events.auth.AccountLockedMessageDTO;
import com.showcase.events.auth.UserDisabledMessageDTO;
import com.showcase.events.auth.UserRegisteredMessageDTO;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Adaptador de entrada de los eventos de auth-service: convierte el mensaje y llama a
 * {@link CustomerAccountService}, sin lógica de negocio ni transacción propia.
 */
@Component
public class UserEventsListener {

    private final CustomerAccountService accountService;

    public UserEventsListener(CustomerAccountService accountService) {
        this.accountService = accountService;
    }

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
