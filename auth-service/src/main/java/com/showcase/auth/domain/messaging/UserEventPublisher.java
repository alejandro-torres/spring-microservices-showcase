package com.showcase.auth.domain.messaging;

import com.showcase.auth.domain.event.AccountLockedEvent;
import com.showcase.auth.domain.event.UserDisabledEvent;
import com.showcase.auth.domain.event.UserRegisteredEvent;

/** Publishing of user events (outbound port), within the transaction of the change. */
public interface UserEventPublisher {

    void publish(UserRegisteredEvent event);

    void publish(UserDisabledEvent event);

    void publish(AccountLockedEvent event);
}
