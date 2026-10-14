package com.showcase.auth.domain.repository;

import com.showcase.auth.domain.model.Role;
import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida para persistir roles.
 */
public interface RoleRepository {

    Role save(Role role);

    /** Solo roles activos. */
    Optional<Role> findById(Long id);

    /** Roles activos con esos ids; los que no existan no aparecen en el resultado. */
    List<Role> findAllById(Collection<Long> ids);

    PageResult<Role> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive);

    boolean existsByName(String name);

    /** Roles activos que incluyen el permiso. */
    long countActiveByPermissionId(Long permissionId);
}
