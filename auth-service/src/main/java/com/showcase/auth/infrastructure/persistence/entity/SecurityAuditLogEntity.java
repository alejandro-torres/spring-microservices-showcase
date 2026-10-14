package com.showcase.auth.infrastructure.persistence.entity;

import com.showcase.auth.domain.model.SecurityEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Registro de auditoría: solo se inserta, nunca se modifica (sin versión ni borrado lógico). */
@Entity
@Table(name = "security_audit_log")
public class SecurityAuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30, updatable = false)
    private SecurityEventType type;

    @Column(nullable = false, length = 100, updatable = false)
    private String username;

    @Column(length = 45, updatable = false)
    private String ip;

    @Column(name = "user_agent", updatable = false)
    private String userAgent;

    @Column(name = "trace_id", length = 64, updatable = false)
    private String traceId;

    @Column(length = 500, updatable = false)
    private String details;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SecurityEventType getType() {
        return type;
    }

    public void setType(SecurityEventType type) {
        this.type = type;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}
