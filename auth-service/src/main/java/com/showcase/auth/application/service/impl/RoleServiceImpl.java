package com.showcase.auth.application.service.impl;

import com.showcase.auth.application.service.RoleService;
import com.showcase.auth.domain.exception.PermissionNotFoundException;
import com.showcase.auth.domain.exception.RoleAlreadyExistsException;
import com.showcase.auth.domain.exception.RoleInUseException;
import com.showcase.auth.domain.exception.RoleNotFoundException;
import com.showcase.auth.domain.model.Permission;
import com.showcase.auth.domain.model.Role;
import com.showcase.auth.domain.repository.PermissionRepository;
import com.showcase.auth.domain.repository.RoleRepository;
import com.showcase.auth.domain.repository.UserRepository;
import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;

    public RoleServiceImpl(RoleRepository roleRepository, PermissionRepository permissionRepository,
                           UserRepository userRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public Role create(String name, String description, Set<Long> permissionIds) {
        Role role = Role.create(name, description, findPermissions(permissionIds));
        if (roleRepository.existsByName(role.getName())) {
            throw new RoleAlreadyExistsException(role.getName());
        }
        return roleRepository.save(role);
    }

    @Override
    public Role findById(Long id) {
        return roleRepository.findById(id).orElseThrow(() -> new RoleNotFoundException(id));
    }

    @Override
    public PageResult<Role> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive) {
        return roleRepository.findAll(filters, pageQuery, includeInactive);
    }

    @Override
    @Transactional
    public Role update(Long id, String description, Set<Long> permissionIds, Long version) {
        Role role = findById(id);
        role.checkVersion(version);
        role.update(description, findPermissions(permissionIds));
        return roleRepository.save(role);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Role role = findById(id);
        long users = userRepository.countActiveByRoleId(id);
        if (users > 0) {
            throw new RoleInUseException(role.getName(), users);
        }
        role.deactivate();
        roleRepository.save(role);
    }

    private Set<Permission> findPermissions(Set<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return Set.of();
        }
        List<Permission> permissions = permissionRepository.findAllById(permissionIds);
        if (permissions.size() != permissionIds.size()) {
            Set<Long> missing = new HashSet<>(permissionIds);
            permissions.forEach(permission -> missing.remove(permission.getId()));
            throw new PermissionNotFoundException(missing.stream().sorted().map(String::valueOf)
                    .collect(Collectors.joining(", ")));
        }
        return new HashSet<>(permissions);
    }
}
