package com.showcase.auth.infrastructure.messaging.publisher.impl;

import com.showcase.auth.domain.event.AccountLockedEvent;
import com.showcase.auth.domain.event.UserDisabledEvent;
import com.showcase.auth.domain.event.UserRegisteredEvent;
import com.showcase.auth.domain.messaging.UserEventPublisher;
import com.showcase.auth.infrastructure.messaging.mapper.UserMessageMapper;
import com.showcase.common.infrastructure.messaging.outbox.OutboxEventWriter;
import com.showcase.events.EventTypes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Guarda los eventos de usuarios en el outbox; el {@code OutboxPublisher} los envía a {@code auth.events}. */
@Component
@RequiredArgsConstructor
public class UserEventPublisherImpl implements UserEventPublisher {

    static final String AGGREGATE_TYPE = "User";

    private final OutboxEventWriter outbox;
    private final UserMessageMapper mapper;

    @Override
    public void publish(UserRegisteredEvent event) {
        outbox.append(AGGREGATE_TYPE, event.userId(), EventTypes.USER_REGISTERED, mapper.toMessage(event),
                event.occurredAt());
    }

    @Override
    public void publish(UserDisabledEvent event) {
        outbox.append(AGGREGATE_TYPE, event.userId(), EventTypes.USER_DISABLED, mapper.toMessage(event),
                event.occurredAt());
    }

    @Override
    public void publish(AccountLockedEvent event) {
        outbox.append(AGGREGATE_TYPE, event.userId(), EventTypes.ACCOUNT_LOCKED, mapper.toMessage(event),
                event.occurredAt());
    }
}
