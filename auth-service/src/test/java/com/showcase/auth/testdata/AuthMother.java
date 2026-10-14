package com.showcase.auth.testdata;

import com.showcase.auth.domain.model.Permission;
import com.showcase.auth.domain.model.Role;
import com.showcase.auth.domain.model.User;
import java.time.Instant;
import java.util.Set;

/** Datos de prueba de auth-service ya persistidos (con id y versión). */
public final class AuthMother {

    public static final Instant CREATED_AT = Instant.parse("2026-10-01T09:00:00Z");
    public static final String PASSWORD_HASH = "{bcrypt}hash-de-prueba-no-es-una-contrasena-real";

    private AuthMother() {
    }

    public static Permission permission(long id, String name) {
        return new Permission(id, name, "Permiso " + name, true, 0L, CREATED_AT, CREATED_AT);
    }

    public static Role role(long id, String name, Permission... permissions) {
        return new Role(id, name, "Rol " + name, Set.of(permissions), true, 0L, CREATED_AT, CREATED_AT);
    }

    public static User user(long id, String username, Role... roles) {
        return new User(id, username, username + "@example.com", PASSWORD_HASH, true, Set.of(roles),
                true, 0L, CREATED_AT, CREATED_AT);
    }

    public static Role adminRole() {
        return role(1L, "ADMIN", permission(1L, "user:read"), permission(2L, "user:write"));
    }

    public static Role userRole() {
        return role(2L, "USER", permission(1L, "user:read"));
    }
}
