package com.showcase.events.auth;

/** Deactivated or deleted user: customer-service marks their customer as inactive. */
public record UserDisabledMessageDTO(Long userId, String username) {
}
