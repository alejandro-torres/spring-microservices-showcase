package com.showcase.auth.infrastructure.persistence.repository;

import com.showcase.auth.infrastructure.persistence.entity.SecurityAuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SecurityAuditJpaRepository
        extends JpaRepository<SecurityAuditLogEntity, Long>, JpaSpecificationExecutor<SecurityAuditLogEntity> {
}
