package com.showcase.auth.infrastructure.persistence.repository.impl;

import static com.showcase.auth.infrastructure.persistence.specification.AuthFilterMappings.PERMISSIONS;

import com.showcase.auth.domain.exception.PermissionNotFoundException;
import com.showcase.auth.domain.model.Permission;
import com.showcase.auth.domain.repository.PermissionRepository;
import com.showcase.auth.infrastructure.persistence.entity.PermissionEntity;
import com.showcase.auth.infrastructure.persistence.mapper.PermissionEntityMapper;
import com.showcase.auth.infrastructure.persistence.repository.PermissionJpaRepository;
import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.common.infrastructure.persistence.page.SpringDataPageAdapter;
import com.showcase.common.infrastructure.persistence.specification.GenericSpecificationBuilder;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

@Repository
public class PermissionRepositoryImpl implements PermissionRepository {

    private final PermissionJpaRepository permissionJpaRepository;
    private final PermissionEntityMapper mapper;
    private final GenericSpecificationBuilder specificationBuilder;

    public PermissionRepositoryImpl(PermissionJpaRepository permissionJpaRepository, PermissionEntityMapper mapper,
                                    GenericSpecificationBuilder specificationBuilder) {
        this.permissionJpaRepository = permissionJpaRepository;
        this.mapper = mapper;
        this.specificationBuilder = specificationBuilder;
    }

    @Override
    public Permission save(Permission permission) {
        PermissionEntity entity;
        if (permission.getId() == null) {
            entity = mapper.toEntity(permission);
        } else {
            entity = permissionJpaRepository.findById(permission.getId())
                    .orElseThrow(() -> new PermissionNotFoundException(permission.getId()));
            mapper.updateEntity(permission, entity);
        }
        return mapper.toDomain(permissionJpaRepository.saveAndFlush(entity));
    }

    @Override
    public Optional<Permission> findById(Long id) {
        return permissionJpaRepository.findByIdAndActiveTrue(id).map(mapper::toDomain);
    }

    @Override
    public List<Permission> findAllById(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return permissionJpaRepository.findAllByIdInAndActiveTrue(ids).stream().map(mapper::toDomain).toList();
    }

    @Override
    public PageResult<Permission> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive) {
        Page<PermissionEntity> page = permissionJpaRepository.findAll(
                specificationBuilder.build(filters, PERMISSIONS, includeInactive),
                SpringDataPageAdapter.toPageable(pageQuery, PERMISSIONS));
        return SpringDataPageAdapter.toPageResult(page, mapper::toDomain);
    }

    @Override
    public boolean existsByName(String name) {
        return permissionJpaRepository.existsByName(name);
    }
}
