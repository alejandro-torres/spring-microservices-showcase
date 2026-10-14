package com.showcase.auth.domain.model;

import static com.showcase.auth.testdata.AuthMother.PASSWORD_HASH;
import static com.showcase.auth.testdata.AuthMother.adminRole;
import static com.showcase.auth.testdata.AuthMother.user;
import static com.showcase.auth.testdata.AuthMother.userRole;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.showcase.auth.domain.exception.AuthErrorCode;
import com.showcase.common.domain.exception.BusinessRuleException;
import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.ConflictException;
import com.showcase.common.domain.exception.InvalidRequestException;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UserTest {

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("crea un usuario activo y habilitado, normalizando username y email")
        void create_shouldNormalizeAndActivate() {
            // when
            var user = User.create("  Ana.Garcia ", " Ana@Example.COM ", PASSWORD_HASH, Set.of(userRole()));

            // then
            assertThat(user.getId()).isNull();
            assertThat(user.getUsername()).isEqualTo("ana.garcia");
            assertThat(user.getEmail()).isEqualTo("ana@example.com");
            assertThat(user.isEnabled()).isTrue();
            assertThat(user.isActive()).isTrue();
            assertThat(user.getRoles()).extracting(Role::getName).containsExactly("USER");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"ab", "con espacios", "demasiado-largo-para-ser-un-username", "ñandu"})
        @DisplayName("rechaza un username con formato inválido con INVALID_USERNAME")
        void create_withInvalidUsername_shouldThrow(String username) {
            assertThatThrownBy(() -> User.create(username, "ana@example.com", PASSWORD_HASH, Set.of()))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_USERNAME);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"sin-arroba", "a@b", "dos@@example.com"})
        @DisplayName("rechaza un email con formato inválido con INVALID_EMAIL")
        void create_withInvalidEmail_shouldThrow(String email) {
            assertThatThrownBy(() -> User.create("ana", email, PASSWORD_HASH, Set.of()))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_EMAIL);
        }

        @Test
        @DisplayName("exige la contraseña ya cifrada")
        void create_withoutPasswordHash_shouldFail() {
            assertThatThrownBy(() -> User.create("ana", "ana@example.com", null, Set.of()))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("roles")
    class Roles {

        @Test
        @DisplayName("asigna un rol nuevo")
        void assignRole_shouldAddRole() {
            var user = user(10L, "ana", userRole());
            user.assignRole(adminRole());
            assertThat(user.getRoles()).extracting(Role::getName).containsExactlyInAnyOrder("USER", "ADMIN");
        }

        @Test
        @DisplayName("rechaza asignar un rol que ya tiene con ROLE_ALREADY_ASSIGNED")
        void assignRole_alreadyAssigned_shouldThrow() {
            var user = user(10L, "ana", userRole());
            assertThatThrownBy(() -> user.assignRole(userRole()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.ROLE_ALREADY_ASSIGNED);
        }

        @Test
        @DisplayName("quita un rol asignado y rechaza quitar uno que no tiene")
        void removeRole_shouldRemoveOrThrow() {
            var user = user(10L, "ana", userRole(), adminRole());

            user.removeRole(adminRole().getId());

            assertThat(user.hasRole(adminRole().getId())).isFalse();
            assertThatThrownBy(() -> user.removeRole(99L))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.ROLE_NOT_ASSIGNED);
        }

        @Test
        @DisplayName("no permite modificar los roles desde fuera del modelo")
        void getRoles_shouldBeUnmodifiable() {
            var user = user(10L, "ana", userRole());
            assertThatThrownBy(() -> user.getRoles().clear()).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("update, deactivate y checkVersion")
    class Lifecycle {

        @Test
        @DisplayName("actualiza el email normalizado y el estado habilitado")
        void update_shouldChangeEmailAndEnabled() {
            var user = user(10L, "ana");
            user.update("NUEVO@example.com", false);
            assertThat(user.getEmail()).isEqualTo("nuevo@example.com");
            assertThat(user.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("el borrado lógico también deshabilita el usuario")
        void deactivate_shouldAlsoDisable() {
            var user = user(10L, "ana");
            user.deactivate();
            assertThat(user.isActive()).isFalse();
            assertThat(user.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("acepta la versión actual y rechaza otra con CONCURRENT_MODIFICATION")
        void checkVersion_shouldDetectConcurrentChanges() {
            var user = user(10L, "ana");
            user.checkVersion(0L);
            assertThatThrownBy(() -> user.checkVersion(1L))
                    .isInstanceOf(ConflictException.class)
                    .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.CONCURRENT_MODIFICATION);
        }
    }
}
