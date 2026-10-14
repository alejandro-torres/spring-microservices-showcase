package com.showcase.auth.infrastructure.persistence.specification;

import com.showcase.common.infrastructure.persistence.specification.FilterMapping;
import java.util.Map;

/**
 * Traducción de los nombres públicos de filtros y ordenación a los paths JPA de cada entidad.
 * Los campos que no aparecen se usan tal cual.
 */
public final class AuthFilterMappings {

    /** {@code role} filtra por el nombre de los roles del usuario (join con user_roles). */
    public static final FilterMapping USERS = FilterMapping.of(Map.of("role", "roles.name"));

    /** {@code permission} filtra por el nombre de los permisos del rol (join con role_permissions). */
    public static final FilterMapping ROLES = FilterMapping.of(Map.of("permission", "permissions.name"));

    public static final FilterMapping PERMISSIONS = FilterMapping.identity();

    private AuthFilterMappings() {
    }
}
