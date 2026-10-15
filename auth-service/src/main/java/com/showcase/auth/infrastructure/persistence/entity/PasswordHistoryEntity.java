package com.showcase.auth.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Entity
@Table(name = "password_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@RequiredArgsConstructor
public class PasswordHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    @NonNull
    private Long userId;

    @Column(name = "password_hash", nullable = false, length = 100, updatable = false)
    @NonNull
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    @NonNull
    private Instant createdAt;

    public String getPasswordHash() {
        return passwordHash;
    }
}
