package com.showcase.common.domain.model;

import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.ConflictException;
import java.time.Instant;
import java.util.Objects;

/**
 * Common data of the business models: identity, soft delete, version for optimistic locking
 * and audit dates (filled in by persistence).
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
     * Checks that the client is modifying the same version it read; if someone else changed it first,
     * throws CONCURRENT_MODIFICATION (409) instead of overwriting their changes.
     */
    public void checkVersion(Long expectedVersion) {
        if (!Objects.equals(version, expectedVersion)) {
            throw new ConflictException(CommonErrorCode.CONCURRENT_MODIFICATION, getClass().getSimpleName(), id);
        }
    }

    /** Soft delete: the record is kept for auditing but is no longer available. */
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
