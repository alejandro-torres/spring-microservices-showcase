package com.showcase.auth.domain.repository;

import com.showcase.auth.domain.model.Permission;
import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port to persist permissions.
 */
public interface PermissionRepository {

    Permission save(Permission permission);

    /** Active permissions only. */
    Optional<Permission> findById(Long id);

    /** Active permissions with those ids; those that do not exist do not appear in the result. */
    List<Permission> findAllById(Collection<Long> ids);

    PageResult<Permission> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive);

    boolean existsByName(String name);
}
