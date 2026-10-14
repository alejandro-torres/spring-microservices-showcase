package com.showcase.events.auth;

/** Alta pública de un usuario: customer-service crea su perfil de cliente. */
public record UserRegisteredMessageDTO(Long userId, String username, String email, String firstName,
                                       String lastName) {
}
