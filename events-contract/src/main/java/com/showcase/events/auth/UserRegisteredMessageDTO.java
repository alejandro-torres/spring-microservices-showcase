package com.showcase.events.auth;

/** Public user sign-up: customer-service creates their customer profile. */
public record UserRegisteredMessageDTO(Long userId, String username, String email, String firstName,
                                       String lastName) {
}
