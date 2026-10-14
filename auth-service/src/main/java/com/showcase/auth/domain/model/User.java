package com.showcase.auth.domain.model;

import com.showcase.auth.domain.exception.AuthErrorCode;
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
 * Usuario del sistema. La contraseña llega ya cifrada: el dominio nunca ve la contraseña en claro.
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
     * Registra un intento de login fallido. Al llegar a {@code maxAttempts} seguidos, bloquea la cuenta
     * durante {@code lockDuration} y reinicia el contador.
     *
     * @return {@code true} si este intento ha provocado el bloqueo
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

    /** Un login correcto reinicia el contador de intentos fallidos. */
    public void resetFailedLogins() {
        failedAttempts = 0;
    }

    /** El bloqueo caduca solo: pasada la fecha {@code lockedUntil}, la cuenta vuelve a estar disponible. */
    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** La comprobación de la contraseña actual y del historial la hace el servicio, que conoce el hasher. */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash, "newPasswordHash");
    }

    /** Desbloqueo manual (lo hace un ADMIN antes de que caduque el bloqueo). */
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

    /** Un usuario borrado tampoco puede iniciar sesión. */
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
