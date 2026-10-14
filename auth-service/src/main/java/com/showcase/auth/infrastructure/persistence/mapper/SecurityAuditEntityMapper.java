package com.showcase.auth.infrastructure.persistence.mapper;

import com.showcase.auth.domain.model.SecurityAuditEvent;
import com.showcase.auth.infrastructure.persistence.entity.SecurityAuditLogEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SecurityAuditEntityMapper {

    SecurityAuditEvent toDomain(SecurityAuditLogEntity entity);

    SecurityAuditLogEntity toEntity(SecurityAuditEvent event);
}
