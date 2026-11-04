package com.showcase.auth.domain.repository;

import com.showcase.auth.domain.model.Role;
import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port to persist roles.
 */
public interface RoleRepository {

    Role save(Role role);

    /** Active roles only. */
    Optional<Role> findById(Long id);

    /** Active roles only. */
    Optional<Role> findByName(String name);

    /** Active roles with those ids; those that do not exist do not appear in the result. */
    List<Role> findAllById(Collection<Long> ids);

    PageResult<Role> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive);

    boolean existsByName(String name);

    /** Active roles that include the permission. */
    long countActiveByPermissionId(Long permissionId);
}
