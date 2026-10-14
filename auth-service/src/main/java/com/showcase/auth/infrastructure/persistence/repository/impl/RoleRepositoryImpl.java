package com.showcase.auth.infrastructure.persistence.repository.impl;

import static com.showcase.auth.infrastructure.persistence.specification.AuthFilterMappings.ROLES;

import com.showcase.auth.domain.exception.RoleNotFoundException;
import com.showcase.auth.domain.model.Permission;
import com.showcase.auth.domain.model.Role;
import com.showcase.auth.domain.repository.RoleRepository;
import com.showcase.auth.infrastructure.persistence.entity.PermissionEntity;
import com.showcase.auth.infrastructure.persistence.entity.RoleEntity;
import com.showcase.auth.infrastructure.persistence.mapper.RoleEntityMapper;
import com.showcase.auth.infrastructure.persistence.repository.PermissionJpaRepository;
import com.showcase.auth.infrastructure.persistence.repository.RoleJpaRepository;
import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.common.infrastructure.persistence.page.SpringDataPageAdapter;
import com.showcase.common.infrastructure.persistence.specification.GenericSpecificationBuilder;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

@Repository
public class RoleRepositoryImpl implements RoleRepository {

    private final RoleJpaRepository roleJpaRepository;
    private final PermissionJpaRepository permissionJpaRepository;
    private final RoleEntityMapper mapper;
    private final GenericSpecificationBuilder specificationBuilder;

    public RoleRepositoryImpl(RoleJpaRepository roleJpaRepository, PermissionJpaRepository permissionJpaRepository,
                              RoleEntityMapper mapper, GenericSpecificationBuilder specificationBuilder) {
        this.roleJpaRepository = roleJpaRepository;
        this.permissionJpaRepository = permissionJpaRepository;
        this.mapper = mapper;
        this.specificationBuilder = specificationBuilder;
    }

    @Override
    public Role save(Role role) {
        RoleEntity entity;
        if (role.getId() == null) {
            entity = mapper.toEntity(role);
        } else {
            entity = roleJpaRepository.findById(role.getId()).orElseThrow(() -> new RoleNotFoundException(role.getId()));
            mapper.updateEntity(role, entity);
        }
        entity.setPermissions(permissionReferences(role.getPermissions()));
        return mapper.toDomain(roleJpaRepository.saveAndFlush(entity));
    }

    @Override
    public Optional<Role> findById(Long id) {
        return roleJpaRepository.findByIdAndActiveTrue(id).map(mapper::toDomain);
    }

    @Override
    public List<Role> findAllById(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return roleJpaRepository.findAllByIdInAndActiveTrue(ids).stream().map(mapper::toDomain).toList();
    }

    @Override
    public PageResult<Role> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive) {
        Page<RoleEntity> page = roleJpaRepository.findAll(
                specificationBuilder.build(filters, ROLES, includeInactive),
                SpringDataPageAdapter.toPageable(pageQuery, ROLES));
        return SpringDataPageAdapter.toPageResult(page, mapper::toDomain);
    }

    @Override
    public boolean existsByName(String name) {
        return roleJpaRepository.existsByName(name);
    }

    @Override
    public long countActiveByPermissionId(Long permissionId) {
        return roleJpaRepository.countActiveByPermissionId(permissionId);
    }

    private Set<PermissionEntity> permissionReferences(Set<Permission> permissions) {
        return permissions.stream()
                .map(permission -> permissionJpaRepository.getReferenceById(permission.getId()))
                .collect(Collectors.toCollection(HashSet::new));
    }
}
