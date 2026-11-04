package com.showcase.auth.application.service;

import com.showcase.auth.domain.model.Role;
import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import java.util.List;
import java.util.Set;

/**
 * Use cases for role management (inbound port).
 */
public interface RoleService {

    Role create(String name, String description, Set<Long> permissionIds);

    Role findById(Long id);

    PageResult<Role> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive);

    Role update(Long id, String description, Set<Long> permissionIds, Long version);

    /** Soft delete; fails if any active user has the role. */
    void delete(Long id);
}
