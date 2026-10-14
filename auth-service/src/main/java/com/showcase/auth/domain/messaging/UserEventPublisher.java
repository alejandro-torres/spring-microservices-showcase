package com.showcase.auth.domain.messaging;

import com.showcase.auth.domain.event.AccountLockedEvent;
import com.showcase.auth.domain.event.UserDisabledEvent;
import com.showcase.auth.domain.event.UserRegisteredEvent;

/** Publicación de los eventos de usuarios (puerto de salida), dentro de la transacción del cambio. */
public interface UserEventPublisher {

    void publish(UserRegisteredEvent event);

    void publish(UserDisabledEvent event);

    void publish(AccountLockedEvent event);
}
