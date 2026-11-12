package com.showcase.auth.infrastructure.rest.controller;

import com.showcase.auth.application.service.TokenRevocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Logout: revokes the token used for the request, which stops being valid instantly
 * instead of waiting for it to expire.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class LogoutController {

    private final TokenRevocationService tokenRevocationService;

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        tokenRevocationService.revoke(jwt.getId(), jwt.getExpiresAt(), jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}
