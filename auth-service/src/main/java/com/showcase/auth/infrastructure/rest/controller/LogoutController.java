package com.showcase.auth.infrastructure.rest.controller;

import com.showcase.auth.application.service.TokenRevocationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cierre de sesión: revoca el token con el que se hace la petición, que deja de valer al instante
 * en lugar de esperar a que caduque.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class LogoutController {

    private final TokenRevocationService tokenRevocationService;

    public LogoutController(TokenRevocationService tokenRevocationService) {
        this.tokenRevocationService = tokenRevocationService;
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        tokenRevocationService.revoke(jwt.getId(), jwt.getExpiresAt(), jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}
