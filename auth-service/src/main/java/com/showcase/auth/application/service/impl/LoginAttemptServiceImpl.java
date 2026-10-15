package com.showcase.auth.application.service.impl;

import com.showcase.auth.application.metrics.AuthMetrics;
import com.showcase.auth.application.security.LoginPolicy;
import com.showcase.auth.application.service.LoginAttemptService;
import com.showcase.auth.application.service.SecurityAuditService;
import com.showcase.auth.domain.event.AccountLockedEvent;
import com.showcase.auth.domain.exception.AccountLockedException;
import com.showcase.auth.domain.messaging.UserEventPublisher;
import com.showcase.auth.domain.model.SecurityEventType;
import com.showcase.auth.domain.model.User;
import com.showcase.auth.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginAttemptServiceImpl implements LoginAttemptService {

    private final UserRepository userRepository;
    private final SecurityAuditService auditService;
    private final LoginPolicy policy;
    private final UserEventPublisher eventPublisher;
    private final AuthMetrics metrics;
    private final Clock clock;

    public LoginAttemptServiceImpl(UserRepository userRepository, SecurityAuditService auditService,
                                   LoginPolicy policy, UserEventPublisher eventPublisher, AuthMetrics metrics,
                                   Clock clock) {
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.policy = policy;
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void onSuccess(String username) {
        findUser(username).ifPresent(user -> {
            if (user.getFailedAttempts() > 0) {
                user.resetFailedLogins();
                userRepository.save(user);
            }
        });
        auditService.record(SecurityEventType.LOGIN_SUCCESS, normalize(username), null);
        metrics.loginSucceeded();
    }

    /**
     * {@code noRollbackFor}: cuando este intento bloquea la cuenta se lanza AccountLockedException para avisar
     * al llamador, pero el contador y la fecha de bloqueo ya modificados tienen que guardarse igualmente.
     * Sin {@code noRollbackFor}, la excepción haría rollback y el bloqueo no llegaría nunca a aplicarse.
     */
    @Override
    @Transactional(noRollbackFor = AccountLockedException.class)
    public void onFailure(String username) {
        Optional<User> found = findUser(username);
        if (found.isEmpty()) {
            // Usuario inexistente: se audita igual (puede ser un ataque), pero no hay cuenta que bloquear.
            auditService.record(SecurityEventType.LOGIN_FAILURE, normalize(username), "unknown user");
            metrics.loginFailed();
            return;
        }
        User user = found.get();
        Instant now = Instant.now(clock);
        boolean lockedNow = user.registerFailedLogin(policy.maxFailedAttempts(), policy.lockDuration(), now);
        userRepository.save(user);
        metrics.loginFailed();
        auditService.record(SecurityEventType.LOGIN_FAILURE, user.getUsername(),
                lockedNow ? "attempts=" + policy.maxFailedAttempts() : "attempts=" + user.getFailedAttempts());
        if (lockedNow) {
            auditService.record(SecurityEventType.ACCOUNT_LOCKED, user.getUsername(), "until=" + user.getLockedUntil());
            // Gracias a noRollbackFor, el evento del outbox (y la métrica) también se confirman aunque se lance la
            // excepción.
            eventPublisher.publish(new AccountLockedEvent(user.getId(), user.getUsername(), user.getLockedUntil(),
                    now));
            metrics.accountLocked();
            throw new AccountLockedException(user.getUsername(), user.getLockedUntil());
        }
    }

    private Optional<User> findUser(String username) {
        String normalized = normalize(username);
        return normalized.isEmpty() ? Optional.empty() : userRepository.findByUsername(normalized);
    }

    // Sin validar el formato: en un login fallido puede llegar cualquier texto.
    private static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
