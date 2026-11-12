package com.showcase.auth.infrastructure.persistence.specification;

import com.showcase.common.infrastructure.persistence.specification.FilterMapping;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Translation of the public filter and sort names to each entity's JPA paths.
 * Fields not listed are used as they are.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AuthFilterMappings {

    /** {@code role} filters by the names of the user's roles (join with user_roles). */
    public static final FilterMapping USERS = FilterMapping.of(Map.of("role", "roles.name"));

    /** {@code permission} filters by the names of the role's permissions (join with role_permissions). */
    public static final FilterMapping ROLES = FilterMapping.of(Map.of("permission", "permissions.name"));

    public static final FilterMapping PERMISSIONS = FilterMapping.identity();
}
