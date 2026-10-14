package com.showcase.auth.application.service.impl;

import com.showcase.auth.application.service.AccountMaintenanceService;
import com.showcase.auth.application.service.SecurityAuditService;
import com.showcase.auth.domain.model.SecurityEventType;
import com.showcase.auth.domain.model.User;
import com.showcase.auth.domain.repository.AuthorizationRepository;
import com.showcase.auth.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El bloqueo de una cuenta caduca solo (el login ya lo ignora pasado {@code lockedUntil}); esta limpieza deja la
 * cuenta en su estado normal y registra el desbloqueo automático en la auditoría. Se procesa en lotes para no
 * tener una transacción larga si se acumulan muchas cuentas.
 */
@Service
public class AccountMaintenanceServiceImpl implements AccountMaintenanceService {

    static final int BATCH_SIZE = 100;

    private final UserRepository userRepository;
    private final AuthorizationRepository authorizationRepository;
    private final SecurityAuditService auditService;
    private final Clock clock;

    public AccountMaintenanceServiceImpl(UserRepository userRepository, AuthorizationRepository authorizationRepository,
                                         SecurityAuditService auditService, Clock clock) {
        this.userRepository = userRepository;
        this.authorizationRepository = authorizationRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public int unlockExpiredAccounts() {
        List<User> users = userRepository.findWithExpiredLock(Instant.now(clock), BATCH_SIZE);
        for (User user : users) {
            user.unlock();
            userRepository.save(user);
            auditService.record(SecurityEventType.ACCOUNT_UNLOCKED, user.getUsername(), "automatic");
        }
        return users.size();
    }

    @Override
    @Transactional
    public int deleteExpiredAuthorizations() {
        return authorizationRepository.deleteFullyExpiredBefore(Instant.now(clock));
    }
}
