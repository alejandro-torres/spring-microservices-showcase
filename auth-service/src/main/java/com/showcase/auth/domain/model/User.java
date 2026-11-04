package com.showcase.auth.domain.model;

import com.showcase.auth.domain.exception.AuthErrorCode;
import com.showcase.common.domain.model.AuditedModel;
import com.showcase.common.domain.exception.BusinessRuleException;
import com.showcase.common.domain.exception.InvalidRequestException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * System user. The password arrives already hashed: the domain never sees the plain-text password.
 */
public class User extends AuditedModel {

    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9._-]{3,30}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final String username;
    private String email;
    private String passwordHash;
    private boolean enabled;
    private final Set<Role> roles;
    private int failedAttempts;
    private Instant lockedUntil;

    public User(Long id, String username, String email, String passwordHash, boolean enabled, Set<Role> roles,
                int failedAttempts, Instant lockedUntil, boolean active, Long version, Instant createdAt,
                Instant updatedAt) {
        super(id, active, version, createdAt, updatedAt);
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.roles = new LinkedHashSet<>(roles == null ? Set.of() : roles);
        this.failedAttempts = failedAttempts;
        this.lockedUntil = lockedUntil;
    }

    public static User create(String username, String email, String passwordHash, Set<Role> roles) {
        Objects.requireNonNull(passwordHash, "passwordHash");
        return new User(null, normalizeUsername(username), normalizeEmail(email), passwordHash, true, roles,
                0, null, true, null, null, null);
    }

    /**
     * Records a failed login attempt. On reaching {@code maxAttempts} in a row, it locks the account
     * for {@code lockDuration} and resets the counter.
     *
     * @return {@code true} if this attempt caused the lock
     */
    public boolean registerFailedLogin(int maxAttempts, Duration lockDuration, Instant now) {
        if (isLocked(now)) {
            return false;
        }
        failedAttempts++;
        if (failedAttempts >= maxAttempts) {
            lockedUntil = now.plus(lockDuration);
            failedAttempts = 0;
            return true;
        }
        return false;
    }

    /** A successful login resets the failed attempts counter. */
    public void resetFailedLogins() {
        failedAttempts = 0;
    }

    /** The lock expires on its own: past the {@code lockedUntil} date, the account is available again. */
    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Checking the current password and the history is done by the service, which knows the hasher. */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash, "newPasswordHash");
    }

    /** Manual unlock (done by an ADMIN before the lock expires). */
    public void unlock() {
        lockedUntil = null;
        failedAttempts = 0;
    }

    public void update(String newEmail, boolean newEnabled) {
        this.email = normalizeEmail(newEmail);
        this.enabled = newEnabled;
    }

    public void assignRole(Role role) {
        if (hasRole(role.getId())) {
            throw new BusinessRuleException(AuthErrorCode.ROLE_ALREADY_ASSIGNED, role.getName(), username);
        }
        roles.add(role);
    }

    public void removeRole(Long roleId) {
        if (!roles.removeIf(role -> Objects.equals(role.getId(), roleId))) {
            throw new BusinessRuleException(AuthErrorCode.ROLE_NOT_ASSIGNED, roleId, username);
        }
    }

    public boolean hasRole(Long roleId) {
        return roles.stream().anyMatch(role -> Objects.equals(role.getId(), roleId));
    }

    /** A deleted user cannot log in either. */
    @Override
    public void deactivate() {
        super.deactivate();
        this.enabled = false;
    }

    public static String normalizeUsername(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME.matcher(normalized).matches()) {
            throw new InvalidRequestException(AuthErrorCode.INVALID_USERNAME, username);
        }
        return normalized;
    }

    public static String normalizeEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(normalized).matches()) {
            throw new InvalidRequestException(AuthErrorCode.INVALID_EMAIL, email);
        }
        return normalized;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }
}
