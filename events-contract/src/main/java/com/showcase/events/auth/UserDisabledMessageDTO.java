package com.showcase.events.auth;

/** Usuario desactivado o borrado: customer-service marca su cliente como inactivo. */
public record UserDisabledMessageDTO(Long userId, String username) {
}
