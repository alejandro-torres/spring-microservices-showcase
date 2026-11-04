package com.showcase.auth.domain.event;

import java.time.Instant;

/**
 * Public user sign-up. Carries the first and last names requested on registration: auth-service does not store
 * them, but customer-service needs them to create the customer profile.
 */
public record UserRegisteredEvent(Long userId, String username, String email, String firstName, String lastName,
                                  Instant occurredAt) {
}
