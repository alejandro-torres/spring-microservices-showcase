package com.showcase.auth.application.service.impl;

import com.showcase.auth.application.metrics.AuthMetrics;
import com.showcase.auth.application.security.PasswordHasher;
import com.showcase.auth.application.service.SecurityAuditService;
import com.showcase.auth.application.service.TokenRevocationService;
import com.showcase.auth.application.service.UserService;
import com.showcase.auth.domain.event.UserDisabledEvent;
import com.showcase.auth.domain.event.UserRegisteredEvent;
import com.showcase.auth.domain.exception.AuthErrorCode;
import com.showcase.auth.domain.exception.EmailAlreadyExistsException;
import com.showcase.auth.domain.exception.RoleNotFoundException;
import com.showcase.auth.domain.exception.UserNotFoundException;
import com.showcase.auth.domain.exception.UsernameAlreadyExistsException;
import com.showcase.auth.domain.messaging.UserEventPublisher;
import com.showcase.auth.domain.model.Role;
import com.showcase.auth.domain.model.SecurityEventType;
import com.showcase.auth.domain.model.User;
import com.showcase.auth.domain.repository.PasswordHistoryRepository;
import com.showcase.auth.domain.repository.RoleRepository;
import com.showcase.auth.domain.repository.UserRepository;
import com.showcase.common.domain.exception.BusinessRuleException;
import com.showcase.common.domain.exception.InvalidRequestException;
import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import io.micrometer.observation.annotation.Observed;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads run in read-only transactions (readOnly at class level) and each write opens its own
 * transaction: if something fails halfway, everything is undone.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    static final String DEFAULT_ROLE = "USER";
    /** Number of previous passwords that cannot be reused (including the current one). */
    static final int PASSWORD_HISTORY_SIZE = 3;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordHasher passwordHasher;
    private final SecurityAuditService auditService;
    private final TokenRevocationService tokenRevocationService;
    private final UserEventPublisher eventPublisher;
    private final AuthMetrics metrics;
    private final Clock clock;

    @Override
    @Transactional
    public User create(String username, String email, String rawPassword, Set<Long> roleIds) {
        return createUser(username, email, rawPassword, () -> findRoles(roleIds));
    }

    /** The user, their roles, their password history and the outbox event are saved together. */
    @Override
    @Transactional
    @Observed(name = "user.register", contextualName = "register user")
    public User register(String username, String email, String rawPassword, String firstName, String lastName) {
        User user = createUser(username, email, rawPassword, () -> Set.of(roleRepository.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new RoleNotFoundException(DEFAULT_ROLE))));
        eventPublisher.publish(new UserRegisteredEvent(user.getId(), user.getUsername(), user.getEmail(),
                firstName, lastName, Instant.now(clock)));
        metrics.userRegistered();
        return user;
    }

    @Override
    public User findById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    @Override
    public User findByUsername(String username) {
        return userRepository.findByUsername(User.normalizeUsername(username))
                .orElseThrow(() -> new UserNotFoundException(username));
    }

    // Uniqueness checks come before resolving the roles and hashing the password (BCrypt is slow).
    private User createUser(String username, String email, String rawPassword, Supplier<Set<Role>> roles) {
        String normalizedUsername = User.normalizeUsername(username);
        String normalizedEmail = User.normalizeEmail(email);
        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new UsernameAlreadyExistsException(normalizedUsername);
        }
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException(normalizedEmail);
        }
        User user = User.create(normalizedUsername, normalizedEmail, passwordHasher.hash(rawPassword), roles.get());
        User saved = userRepository.save(user);
        passwordHistoryRepository.save(saved.getId(), saved.getPasswordHash(), Instant.now(clock));
        return saved;
    }

    @Override
    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword) {
        User user = findByUsername(username);
        if (!passwordHasher.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidRequestException(AuthErrorCode.INVALID_CURRENT_PASSWORD);
        }
        if (isRecentlyUsed(user, newPassword)) {
            throw new BusinessRuleException(AuthErrorCode.PASSWORD_RECENTLY_USED, PASSWORD_HISTORY_SIZE);
        }
        user.changePassword(passwordHasher.hash(newPassword));
        userRepository.save(user);
        passwordHistoryRepository.save(user.getId(), user.getPasswordHash(), Instant.now(clock));
        auditService.record(SecurityEventType.PASSWORD_CHANGED, user.getUsername(), null);
        // If someone had a stolen token, it stops being valid when the password changes.
        tokenRevocationService.revokeAllForUser(user.getUsername(), "password changed");
    }

    // The current one always counts, even if the user predates the history (sample data).
    private boolean isRecentlyUsed(User user, String newPassword) {
        if (passwordHasher.matches(newPassword, user.getPasswordHash())) {
            return true;
        }
        return passwordHistoryRepository.findRecentHashes(user.getId(), PASSWORD_HISTORY_SIZE).stream()
                .anyMatch(hash -> passwordHasher.matches(newPassword, hash));
    }

    @Override
    public PageResult<User> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive) {
        return userRepository.findAll(filters, pageQuery, includeInactive);
    }

    @Override
    @Transactional
    public User update(Long id, String email, boolean enabled, Long version) {
        User user = findById(id);
        user.checkVersion(version);
        String normalizedEmail = User.normalizeEmail(email);
        if (userRepository.existsByEmailAndIdNot(normalizedEmail, id)) {
            throw new EmailAlreadyExistsException(normalizedEmail);
        }
        boolean wasEnabled = user.isEnabled();
        user.update(normalizedEmail, enabled);
        User saved = userRepository.save(user);
        if (wasEnabled && !enabled) {
            auditService.record(SecurityEventType.USER_DISABLED, saved.getUsername(), "enabled=false");
            tokenRevocationService.revokeAllForUser(saved.getUsername(), "user disabled");
            eventPublisher.publish(new UserDisabledEvent(saved.getId(), saved.getUsername(), Instant.now(clock)));
        }
        return saved;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        User user = findById(id);
        user.deactivate();
        userRepository.save(user);
        auditService.record(SecurityEventType.USER_DISABLED, user.getUsername(), "deleted");
        tokenRevocationService.revokeAllForUser(user.getUsername(), "user deleted");
        eventPublisher.publish(new UserDisabledEvent(user.getId(), user.getUsername(), Instant.now(clock)));
    }

    @Override
    @Transactional
    public User assignRole(Long userId, Long roleId) {
        User user = findById(userId);
        Role role = roleRepository.findById(roleId).orElseThrow(() -> new RoleNotFoundException(roleId));
        user.assignRole(role);
        User saved = userRepository.save(user);
        auditService.record(SecurityEventType.ROLE_ASSIGNED, saved.getUsername(), "role=" + role.getName());
        return saved;
    }

    @Override
    @Transactional
    public User removeRole(Long userId, Long roleId) {
        User user = findById(userId);
        String roleName = user.getRoles().stream().filter(role -> roleId.equals(role.getId()))
                .map(Role::getName).findFirst().orElse(String.valueOf(roleId));
        user.removeRole(roleId);
        User saved = userRepository.save(user);
        auditService.record(SecurityEventType.ROLE_REMOVED, saved.getUsername(), "role=" + roleName);
        // Tokens already issued carry the role in their claims: they are revoked so the change is immediate.
        tokenRevocationService.revokeAllForUser(saved.getUsername(), "role removed");
        return saved;
    }

    @Override
    @Transactional
    public User unlock(Long id) {
        User user = findById(id);
        user.unlock();
        User saved = userRepository.save(user);
        auditService.record(SecurityEventType.ACCOUNT_UNLOCKED, saved.getUsername(), null);
        return saved;
    }

    // All the requested roles must exist and be active; if any is missing, it says which.
    private Set<Role> findRoles(Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Set.of();
        }
        List<Role> roles = roleRepository.findAllById(roleIds);
        if (roles.size() != roleIds.size()) {
            Set<Long> missing = new HashSet<>(roleIds);
            roles.forEach(role -> missing.remove(role.getId()));
            throw new RoleNotFoundException(missing.stream().sorted().map(String::valueOf)
                    .collect(Collectors.joining(", ")));
        }
        return new HashSet<>(roles);
    }
}
