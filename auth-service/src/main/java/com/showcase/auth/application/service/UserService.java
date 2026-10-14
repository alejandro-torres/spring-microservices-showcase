package com.showcase.auth.application.service;

import com.showcase.auth.domain.model.User;
import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import java.util.List;
import java.util.Set;

/**
 * Casos de uso de gestión de usuarios (puerto de entrada).
 */
public interface UserService {

    User create(String username, String email, String rawPassword, Set<Long> roleIds);

    /** Alta pública: el usuario recibe siempre el rol USER. */
    User register(String username, String email, String rawPassword);

    User findById(Long id);

    User findByUsername(String username);

    PageResult<User> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive);

    /** Actualiza el email y el estado; {@code version} es la que leyó el cliente (bloqueo optimista). */
    User update(Long id, String email, boolean enabled, Long version);

    /** Borrado lógico. */
    void delete(Long id);

    User assignRole(Long userId, Long roleId);

    User removeRole(Long userId, Long roleId);

    /** Desbloqueo manual de una cuenta bloqueada por intentos fallidos. */
    User unlock(Long id);

    /**
     * Cambio de contraseña del propio usuario: exige la actual e impide reutilizar las últimas.
     */
    void changePassword(String username, String currentPassword, String newPassword);
}
