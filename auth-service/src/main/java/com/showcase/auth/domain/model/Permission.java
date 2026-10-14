package com.showcase.auth.domain.model;

import com.showcase.auth.domain.exception.AuthErrorCode;
import com.showcase.common.domain.model.AuditedModel;
import com.showcase.common.domain.exception.InvalidRequestException;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Permiso sobre un recurso con el formato {@code recurso:accion} (p. ej. {@code product:write}).
 * Acaba como claim en el JWT y se comprueba con {@code @PreAuthorize}.
 */
public class Permission extends AuditedModel {

    private static final Pattern NAME = Pattern.compile("^[a-z][a-z-]*:[a-z][a-z-]*$");

    private final String name;
    private String description;

    public Permission(Long id, String name, String description, boolean active, Long version,
                      Instant createdAt, Instant updatedAt) {
        super(id, active, version, createdAt, updatedAt);
        this.name = name;
        this.description = description;
    }

    public static Permission create(String name, String description) {
        String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (!NAME.matcher(normalized).matches()) {
            throw new InvalidRequestException(AuthErrorCode.INVALID_PERMISSION_NAME, name);
        }
        return new Permission(null, normalized, description, true, null, null, null);
    }

    public void updateDescription(String newDescription) {
        this.description = newDescription;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
