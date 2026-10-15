package com.showcase.auth.application.service.impl;

import com.showcase.auth.application.service.PermissionService;
import com.showcase.auth.domain.exception.PermissionAlreadyExistsException;
import com.showcase.auth.domain.exception.PermissionInUseException;
import com.showcase.auth.domain.exception.PermissionNotFoundException;
import com.showcase.auth.domain.model.Permission;
import com.showcase.auth.domain.repository.PermissionRepository;
import com.showcase.auth.domain.repository.RoleRepository;
import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;

    @Override
    @Transactional
    public Permission create(String name, String description) {
        Permission permission = Permission.create(name, description);
        if (permissionRepository.existsByName(permission.getName())) {
            throw new PermissionAlreadyExistsException(permission.getName());
        }
        return permissionRepository.save(permission);
    }

    @Override
    public Permission findById(Long id) {
        return permissionRepository.findById(id).orElseThrow(() -> new PermissionNotFoundException(id));
    }

    @Override
    public PageResult<Permission> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive) {
        return permissionRepository.findAll(filters, pageQuery, includeInactive);
    }

    @Override
    @Transactional
    public Permission update(Long id, String description, Long version) {
        Permission permission = findById(id);
        permission.checkVersion(version);
        permission.updateDescription(description);
        return permissionRepository.save(permission);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Permission permission = findById(id);
        long roles = roleRepository.countActiveByPermissionId(id);
        if (roles > 0) {
            throw new PermissionInUseException(permission.getName(), roles);
        }
        permission.deactivate();
        permissionRepository.save(permission);
    }
}
