package com.showcase.auth.domain.model;

import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.ConflictException;
import java.time.Instant;
import java.util.Objects;

/**
 * Datos comunes de los modelos de negocio: identidad, borrado lógico, versión para el bloqueo
 * optimista y fechas de auditoría (las rellena la persistencia).
 */
public abstract class AuditedModel {

    private final Long id;
    private boolean active;
    private final Long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    protected AuditedModel(Long id, boolean active, Long version, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.active = active;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Comprueba que el cliente modifica la misma versión que leyó; si otro la cambió antes,
     * lanza CONCURRENT_MODIFICATION (409) en lugar de sobrescribir sus cambios.
     */
    public void checkVersion(Long expectedVersion) {
        if (!Objects.equals(version, expectedVersion)) {
            throw new ConflictException(CommonErrorCode.CONCURRENT_MODIFICATION, getClass().getSimpleName(), id);
        }
    }

    /** Borrado lógico: el registro se conserva para auditoría pero deja de estar disponible. */
    public void deactivate() {
        this.active = false;
    }

    public Long getId() {
        return id;
    }

    public boolean isActive() {
        return active;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
