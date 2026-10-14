package com.showcase.auth.infrastructure.security;

import com.showcase.auth.application.service.LoginAttemptService;
import com.showcase.auth.application.service.SecurityAuditService;
import com.showcase.auth.domain.exception.AccountLockedException;
import com.showcase.auth.domain.model.SecurityEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * Traduce los eventos de login de Spring Security a los casos de uso de seguimiento de intentos.
 * Solo atiende al login con usuario y contraseña (no a la validación de cada JWT ni a client_credentials).
 */
@Component
public class AuthenticationEventsListener {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationEventsListener.class);

    private final LoginAttemptService loginAttemptService;
    private final SecurityAuditService auditService;

    public AuthenticationEventsListener(LoginAttemptService loginAttemptService, SecurityAuditService auditService) {
        this.loginAttemptService = loginAttemptService;
        this.auditService = auditService;
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        if (event.getAuthentication() instanceof UsernamePasswordAuthenticationToken authentication) {
            loginAttemptService.onSuccess(authentication.getName());
        }
    }

    @EventListener
    public void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        if (!(event.getAuthentication() instanceof UsernamePasswordAuthenticationToken)) {
            return;
        }
        try {
            loginAttemptService.onFailure(event.getAuthentication().getName());
        } catch (AccountLockedException e) {
            log.warn("Cuenta bloqueada por intentos fallidos: {}", e.getArgs().getFirst());
        }
    }

    /** Intentos sobre una cuenta ya bloqueada: no cuentan, pero se auditan. */
    @EventListener
    public void onLocked(AuthenticationFailureLockedEvent event) {
        auditService.record(SecurityEventType.LOGIN_FAILURE, event.getAuthentication().getName(), "account locked");
    }
}
