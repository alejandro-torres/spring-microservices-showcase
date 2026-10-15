package com.showcase.auth.infrastructure.persistence.repository.impl;

import com.showcase.auth.domain.model.SecurityAuditEvent;
import com.showcase.auth.domain.repository.SecurityAuditRepository;
import com.showcase.auth.infrastructure.persistence.entity.SecurityAuditLogEntity;
import com.showcase.auth.infrastructure.persistence.mapper.SecurityAuditEntityMapper;
import com.showcase.auth.infrastructure.persistence.repository.SecurityAuditJpaRepository;
import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.common.infrastructure.persistence.page.SpringDataPageAdapter;
import com.showcase.common.infrastructure.persistence.specification.FilterMapping;
import com.showcase.common.infrastructure.persistence.specification.GenericSpecificationBuilder;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SecurityAuditRepositoryImpl implements SecurityAuditRepository {

    private static final FilterMapping MAPPING = FilterMapping.of(Map.of("eventType", "type"));

    private final SecurityAuditJpaRepository jpaRepository;
    private final SecurityAuditEntityMapper mapper;
    private final GenericSpecificationBuilder specificationBuilder;

    @Override
    public SecurityAuditEvent save(SecurityAuditEvent event) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(event)));
    }

    /** La auditoría no tiene borrado lógico: se pide incluir "inactivos" para no filtrar por la columna active. */
    @Override
    public PageResult<SecurityAuditEvent> findAll(List<FilterCriterion> filters, PageQuery pageQuery) {
        Page<SecurityAuditLogEntity> page = jpaRepository.findAll(
                specificationBuilder.build(filters, MAPPING, true),
                SpringDataPageAdapter.toPageable(pageQuery, MAPPING));
        return SpringDataPageAdapter.toPageResult(page, mapper::toDomain);
    }
}
