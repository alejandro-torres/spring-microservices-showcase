package com.showcase.auth.domain.repository;

import com.showcase.auth.domain.model.User;
import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port to persist users. The infrastructure implements it with JPA.
 */
public interface UserRepository {

    User save(User user);

    /** Active users only (not deleted). */
    Optional<User> findById(Long id);

    /** Active users only; the username arrives already normalized (lowercase). */
    Optional<User> findByUsername(String username);

    /** Up to {@code limit} active accounts whose failed-attempts lock has already expired. */
    List<User> findWithExpiredLock(Instant now, int limit);

    PageResult<User> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    /** Active users assigned the role. */
    long countActiveByRoleId(Long roleId);
}
