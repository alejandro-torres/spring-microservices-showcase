package com.showcase.auth.application.service;

import com.showcase.auth.domain.model.Role;
import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import java.util.List;
import java.util.Set;

/**
 * Casos de uso de gestión de roles (puerto de entrada).
 */
public interface RoleService {

    Role create(String name, String description, Set<Long> permissionIds);

    Role findById(Long id);

    PageResult<Role> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive);

    Role update(Long id, String description, Set<Long> permissionIds, Long version);

    /** Borrado lógico; falla si algún usuario activo tiene el rol. */
    void delete(Long id);
}
