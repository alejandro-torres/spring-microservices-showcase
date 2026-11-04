package com.showcase.auth.application.service;

import com.showcase.auth.domain.model.User;
import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import java.util.List;
import java.util.Set;

/**
 * Use cases for user management (inbound port).
 */
public interface UserService {

    User create(String username, String email, String rawPassword, Set<Long> roleIds);

    /**
     * Public sign-up: the user always gets the USER role. The first and last names are not stored here: they travel
     * in the event so that customer-service creates the customer profile.
     */
    User register(String username, String email, String rawPassword, String firstName, String lastName);

    User findById(Long id);

    User findByUsername(String username);

    PageResult<User> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive);

    /** Updates the email and status; {@code version} is the one the client read (optimistic locking). */
    User update(Long id, String email, boolean enabled, Long version);

    /** Soft delete. */
    void delete(Long id);

    User assignRole(Long userId, Long roleId);

    User removeRole(Long userId, Long roleId);

    /** Manual unlock of an account locked by failed attempts. */
    User unlock(Long id);

    /**
     * A user changing their own password: requires the current one and prevents reusing the latest ones.
     */
    void changePassword(String username, String currentPassword, String newPassword);
}
