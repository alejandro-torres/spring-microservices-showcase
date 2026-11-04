package com.showcase.auth.domain.model;

import static com.showcase.auth.testdata.AuthMother.permission;
import static com.showcase.auth.testdata.AuthMother.role;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.showcase.auth.domain.exception.AuthErrorCode;
import com.showcase.common.domain.exception.InvalidRequestException;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RoleAndPermissionTest {

    @Nested
    @DisplayName("Role")
    class RoleTests {

        @Test
        @DisplayName("creates an active role with the name in uppercase")
        void create_shouldUppercaseName() {
            var role = Role.create(" support ", "Support", Set.of(permission(1L, "user:read")));
            assertThat(role.getName()).isEqualTo("SUPPORT");
            assertThat(role.isActive()).isTrue();
            assertThat(role.permissionNames()).containsExactly("user:read");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"A", "ROLE-WITH-HYPHEN", "ROL 1", "1ADMIN"})
        @DisplayName("rejects names with an invalid format with INVALID_ROLE_NAME")
        void create_withInvalidName_shouldThrow(String name) {
            assertThatThrownBy(() -> Role.create(name, null, Set.of()))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_ROLE_NAME);
        }

        @Test
        @DisplayName("update replaces the description and the set of permissions")
        void update_shouldReplacePermissions() {
            var role = role(1L, "ADMIN", permission(1L, "user:read"));
            role.update("Administration", Set.of(permission(2L, "user:write"), permission(3L, "role:write")));
            assertThat(role.getDescription()).isEqualTo("Administration");
            assertThat(role.permissionNames()).containsExactlyInAnyOrder("user:write", "role:write");
        }
    }

    @Nested
    @DisplayName("Permission")
    class PermissionTests {

        @Test
        @DisplayName("creates a permission in resource:action format in lowercase")
        void create_shouldLowercaseName() {
            var permission = Permission.create("Product:Write", "Create and edit products");
            assertThat(permission.getName()).isEqualTo("product:write");
            assertThat(permission.isActive()).isTrue();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"product", "product:", ":write", "product:write:all", "product write"})
        @DisplayName("rejects names not following resource:action with INVALID_PERMISSION_NAME")
        void create_withInvalidName_shouldThrow(String name) {
            assertThatThrownBy(() -> Permission.create(name, null))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_PERMISSION_NAME);
        }

        @Test
        @DisplayName("updates the description and allows soft delete")
        void updateAndDeactivate_shouldWork() {
            var permission = permission(1L, "user:read");
            permission.updateDescription("View users");
            permission.deactivate();
            assertThat(permission.getDescription()).isEqualTo("View users");
            assertThat(permission.isActive()).isFalse();
        }
    }
}
