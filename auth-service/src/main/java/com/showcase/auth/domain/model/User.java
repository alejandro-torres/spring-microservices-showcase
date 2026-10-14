package com.showcase.auth.domain.model;

import com.showcase.auth.domain.exception.AuthErrorCode;
import com.showcase.common.domain.exception.BusinessRuleException;
import com.showcase.common.domain.exception.InvalidRequestException;
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
    private final String passwordHash;
    private boolean enabled;
    private final Set<Role> roles;

    public User(Long id, String username, String email, String passwordHash, boolean enabled, Set<Role> roles,
                boolean active, Long version, Instant createdAt, Instant updatedAt) {
        super(id, active, version, createdAt, updatedAt);
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.roles = new LinkedHashSet<>(roles == null ? Set.of() : roles);
    }

    public static User create(String username, String email, String passwordHash, Set<Role> roles) {
        Objects.requireNonNull(passwordHash, "passwordHash");
        return new User(null, normalizeUsername(username), normalizeEmail(email), passwordHash, true, roles,
                true, null, null, null);
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
}
