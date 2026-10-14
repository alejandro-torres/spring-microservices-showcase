package com.showcase.auth.infrastructure.messaging.mapper;

import com.showcase.auth.domain.event.AccountLockedEvent;
import com.showcase.auth.domain.event.UserDisabledEvent;
import com.showcase.auth.domain.event.UserRegisteredEvent;
import com.showcase.events.auth.AccountLockedMessageDTO;
import com.showcase.events.auth.UserDisabledMessageDTO;
import com.showcase.events.auth.UserRegisteredMessageDTO;
import org.mapstruct.Mapper;

/** Eventos de dominio de usuarios → MessageDTO de events-contract. */
@Mapper(componentModel = "spring")
public interface UserMessageMapper {

    UserRegisteredMessageDTO toMessage(UserRegisteredEvent event);

    UserDisabledMessageDTO toMessage(UserDisabledEvent event);

    AccountLockedMessageDTO toMessage(AccountLockedEvent event);
}
