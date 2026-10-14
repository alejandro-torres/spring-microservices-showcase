package com.showcase.auth.domain.repository;

import com.showcase.auth.domain.model.User;
import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida para persistir usuarios. Lo implementa la infraestructura con JPA.
 */
public interface UserRepository {

    User save(User user);

    /** Solo usuarios activos (no borrados). */
    Optional<User> findById(Long id);

    /** Solo usuarios activos; el username ya llega normalizado (minúsculas). */
    Optional<User> findByUsername(String username);

    /** Hasta {@code limit} cuentas activas cuyo bloqueo por intentos fallidos ya ha caducado. */
    List<User> findWithExpiredLock(Instant now, int limit);

    PageResult<User> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    /** Usuarios activos que tienen asignado el rol. */
    long countActiveByRoleId(Long roleId);
}
