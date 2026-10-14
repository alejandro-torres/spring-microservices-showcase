package com.showcase.auth.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "password_history")
public class PasswordHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "password_hash", nullable = false, length = 100, updatable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PasswordHistoryEntity() {
    }

    public PasswordHistoryEntity(Long userId, String passwordHash, Instant createdAt) {
        this.userId = userId;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    public String getPasswordHash() {
        return passwordHash;
    }
}
