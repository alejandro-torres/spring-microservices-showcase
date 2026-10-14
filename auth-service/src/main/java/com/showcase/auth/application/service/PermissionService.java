package com.showcase.auth.application.service;

import com.showcase.auth.domain.model.Permission;
import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import java.util.List;

/**
 * Casos de uso de gestión de permisos (puerto de entrada).
 */
public interface PermissionService {

    Permission create(String name, String description);

    Permission findById(Long id);

    PageResult<Permission> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive);

    Permission update(Long id, String description, Long version);

    /** Borrado lógico; falla si algún rol activo incluye el permiso. */
    void delete(Long id);
}
