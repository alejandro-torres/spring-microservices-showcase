package com.showcase.auth.domain.repository;

import com.showcase.auth.domain.model.Permission;
import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida para persistir permisos.
 */
public interface PermissionRepository {

    Permission save(Permission permission);

    /** Solo permisos activos. */
    Optional<Permission> findById(Long id);

    /** Permisos activos con esos ids; los que no existan no aparecen en el resultado. */
    List<Permission> findAllById(Collection<Long> ids);

    PageResult<Permission> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive);

    boolean existsByName(String name);
}
