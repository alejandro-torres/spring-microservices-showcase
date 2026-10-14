package com.showcase.auth.application.service.impl;

import static com.showcase.auth.testdata.AuthMother.adminRole;
import static com.showcase.auth.testdata.AuthMother.permission;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.showcase.auth.domain.exception.AuthErrorCode;
import com.showcase.auth.domain.exception.PermissionNotFoundException;
import com.showcase.auth.domain.exception.RoleAlreadyExistsException;
import com.showcase.auth.domain.exception.RoleInUseException;
import com.showcase.auth.domain.exception.RoleNotFoundException;
import com.showcase.auth.domain.model.Role;
import com.showcase.auth.domain.repository.PermissionRepository;
import com.showcase.auth.domain.repository.RoleRepository;
import com.showcase.auth.domain.repository.UserRepository;
import com.showcase.common.domain.exception.ConflictException;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RoleServiceImpl roleService;

    @Captor
    private ArgumentCaptor<Role> roleCaptor;

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("crea el rol con el nombre normalizado y sus permisos")
        void create_shouldSaveRoleWithPermissions() {
            given(permissionRepository.findAllById(Set.of(1L))).willReturn(List.of(permission(1L, "user:read")));
            given(roleRepository.save(any(Role.class))).willAnswer(invocation -> invocation.getArgument(0));

            var created = roleService.create("support", "Soporte", Set.of(1L));

            assertThat(created.getName()).isEqualTo("SUPPORT");
            assertThat(created.permissionNames()).containsExactly("user:read");
        }

        @Test
        @DisplayName("rechaza un nombre de rol ya existente")
        void create_withExistingName_shouldThrow() {
            given(roleRepository.existsByName("ADMIN")).willReturn(true);

            assertThatThrownBy(() -> roleService.create("admin", null, Set.of()))
                    .isInstanceOf(RoleAlreadyExistsException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.ROLE_ALREADY_EXISTS);
            verify(roleRepository, never()).save(any());
        }

        @Test
        @DisplayName("rechaza permisos inexistentes")
        void create_withMissingPermission_shouldThrow() {
            given(permissionRepository.findAllById(Set.of(1L, 50L))).willReturn(List.of(permission(1L, "user:read")));

            assertThatThrownBy(() -> roleService.create("SUPPORT", null, Set.of(1L, 50L)))
                    .isInstanceOf(PermissionNotFoundException.class)
                    .satisfies(e -> assertThat(((PermissionNotFoundException) e).getArgs()).containsExactly("50"));
        }
    }

    @Nested
    @DisplayName("findById, findAll y update")
    class FindAndUpdate {

        @Test
        @DisplayName("findById lanza ROLE_NOT_FOUND si no existe y findAll delega en el repositorio")
        void find_shouldThrowOrDelegate() {
            var page = PageResult.of(List.of(adminRole()), 0, 10, 1);
            given(roleRepository.findAll(List.of(), PageQuery.firstPage(), false)).willReturn(page);

            assertThatThrownBy(() -> roleService.findById(1L)).isInstanceOf(RoleNotFoundException.class);
            assertThat(roleService.findAll(List.of(), PageQuery.firstPage(), false)).isSameAs(page);
        }

        @Test
        @DisplayName("update sustituye descripción y permisos si la versión coincide")
        void update_shouldReplacePermissions() {
            given(roleRepository.findById(1L)).willReturn(Optional.of(adminRole()));
            given(permissionRepository.findAllById(Set.of(3L))).willReturn(List.of(permission(3L, "role:read")));
            given(roleRepository.save(any(Role.class))).willAnswer(invocation -> invocation.getArgument(0));

            var updated = roleService.update(1L, "Solo lectura", Set.of(3L), 0L);

            assertThat(updated.getDescription()).isEqualTo("Solo lectura");
            assertThat(updated.permissionNames()).containsExactly("role:read");
        }

        @Test
        @DisplayName("update rechaza una versión desactualizada")
        void update_withStaleVersion_shouldThrow() {
            given(roleRepository.findById(1L)).willReturn(Optional.of(adminRole()));

            assertThatThrownBy(() -> roleService.update(1L, null, Set.of(), 5L)).isInstanceOf(ConflictException.class);
            verify(roleRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("hace el borrado lógico si ningún usuario activo tiene el rol")
        void delete_unusedRole_shouldDeactivate() {
            given(roleRepository.findById(1L)).willReturn(Optional.of(adminRole()));
            given(userRepository.countActiveByRoleId(1L)).willReturn(0L);

            roleService.delete(1L);

            verify(roleRepository).save(roleCaptor.capture());
            assertThat(roleCaptor.getValue().isActive()).isFalse();
        }

        @Test
        @DisplayName("rechaza borrar un rol asignado con ROLE_IN_USE (409)")
        void delete_roleInUse_shouldThrow() {
            given(roleRepository.findById(1L)).willReturn(Optional.of(adminRole()));
            given(userRepository.countActiveByRoleId(1L)).willReturn(3L);

            assertThatThrownBy(() -> roleService.delete(1L))
                    .isInstanceOf(RoleInUseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.ROLE_IN_USE);
            verify(roleRepository, never()).save(any());
        }
    }
}
