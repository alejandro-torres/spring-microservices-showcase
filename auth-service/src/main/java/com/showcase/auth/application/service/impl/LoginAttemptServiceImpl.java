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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginAttemptServiceImpl implements LoginAttemptService {

    private final UserRepository userRepository;
    private final SecurityAuditService auditService;
    private final LoginPolicy policy;
    private final UserEventPublisher eventPublisher;
    private final AuthMetrics metrics;
    private final Clock clock;

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
     * {@code noRollbackFor}: when this attempt locks the account, AccountLockedException is thrown to warn
     * the caller, but the counter and lock date already modified must be saved anyway.
     * Without {@code noRollbackFor}, the exception would roll back and the lock would never be applied.
     */
    @Override
    @Transactional(noRollbackFor = AccountLockedException.class)
    public void onFailure(String username) {
        Optional<User> found = findUser(username);
        if (found.isEmpty()) {
            // Unknown user: audited all the same (it may be an attack), but there is no account to lock.
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
            // Thanks to noRollbackFor, the outbox event (and the metric) also commit even though the exception is
            // thrown.
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

    // Without validating the format: any text can arrive in a failed login.
    private static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
