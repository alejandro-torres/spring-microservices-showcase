package com.showcase.auth.domain.model;

import com.showcase.auth.domain.exception.AuthErrorCode;
import com.showcase.common.domain.model.AuditedModel;
import com.showcase.common.domain.exception.InvalidRequestException;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Rol con un conjunto de permisos (p. ej. ADMIN, USER).
 */
public class Role extends AuditedModel {

    private static final Pattern NAME = Pattern.compile("^[A-Z][A-Z_]{1,29}$");

    private final String name;
    private String description;
    private final Set<Permission> permissions;

    public Role(Long id, String name, String description, Set<Permission> permissions, boolean active,
                Long version, Instant createdAt, Instant updatedAt) {
        super(id, active, version, createdAt, updatedAt);
        this.name = name;
        this.description = description;
        this.permissions = new LinkedHashSet<>(permissions == null ? Set.of() : permissions);
    }

    public static Role create(String name, String description, Set<Permission> permissions) {
        String normalized = name == null ? "" : name.trim().toUpperCase(Locale.ROOT);
        if (!NAME.matcher(normalized).matches()) {
            throw new InvalidRequestException(AuthErrorCode.INVALID_ROLE_NAME, name);
        }
        return new Role(null, normalized, description, permissions, true, null, null, null);
    }

    /** Sustituye la descripción y el conjunto completo de permisos. */
    public void update(String newDescription, Set<Permission> newPermissions) {
        this.description = newDescription;
        this.permissions.clear();
        this.permissions.addAll(newPermissions);
    }

    public Set<String> permissionNames() {
        return permissions.stream().map(Permission::getName).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Set<Permission> getPermissions() {
        return Collections.unmodifiableSet(permissions);
    }
}
