package com.showcase.auth.application.service.impl;

import static com.showcase.auth.testdata.AuthMother.permission;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.showcase.auth.domain.exception.AuthErrorCode;
import com.showcase.auth.domain.exception.PermissionAlreadyExistsException;
import com.showcase.auth.domain.exception.PermissionInUseException;
import com.showcase.auth.domain.exception.PermissionNotFoundException;
import com.showcase.auth.domain.model.Permission;
import com.showcase.auth.domain.repository.PermissionRepository;
import com.showcase.auth.domain.repository.RoleRepository;
import com.showcase.common.domain.exception.ConflictException;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import java.util.List;
import java.util.Optional;
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
class PermissionServiceImplTest {

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private PermissionServiceImpl permissionService;

    @Captor
    private ArgumentCaptor<Permission> permissionCaptor;

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("crea el permiso con el nombre normalizado")
        void create_shouldSaveNormalizedPermission() {
            given(permissionRepository.save(any(Permission.class))).willAnswer(invocation -> invocation.getArgument(0));

            assertThat(permissionService.create("Report:Read", "Ver informes").getName()).isEqualTo("report:read");
        }

        @Test
        @DisplayName("rechaza un permiso ya existente")
        void create_withExistingName_shouldThrow() {
            given(permissionRepository.existsByName("user:read")).willReturn(true);

            assertThatThrownBy(() -> permissionService.create("user:read", null))
                    .isInstanceOf(PermissionAlreadyExistsException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.PERMISSION_ALREADY_EXISTS);
            verify(permissionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("findById, findAll y update")
    class FindAndUpdate {

        @Test
        @DisplayName("findById lanza PERMISSION_NOT_FOUND si no existe y findAll delega en el repositorio")
        void find_shouldThrowOrDelegate() {
            var page = PageResult.of(List.of(permission(1L, "user:read")), 0, 10, 1);
            given(permissionRepository.findAll(List.of(), PageQuery.firstPage(), false)).willReturn(page);

            assertThatThrownBy(() -> permissionService.findById(1L)).isInstanceOf(PermissionNotFoundException.class);
            assertThat(permissionService.findAll(List.of(), PageQuery.firstPage(), false)).isSameAs(page);
        }

        @Test
        @DisplayName("update cambia la descripción si la versión coincide y la rechaza si no")
        void update_shouldCheckVersion() {
            given(permissionRepository.findById(1L)).willReturn(Optional.of(permission(1L, "user:read")));
            given(permissionRepository.save(any(Permission.class))).willAnswer(invocation -> invocation.getArgument(0));

            assertThat(permissionService.update(1L, "Ver usuarios", 0L).getDescription()).isEqualTo("Ver usuarios");
            assertThatThrownBy(() -> permissionService.update(1L, "Otra", 9L)).isInstanceOf(ConflictException.class);
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("hace el borrado lógico si ningún rol activo lo usa")
        void delete_unusedPermission_shouldDeactivate() {
            given(permissionRepository.findById(1L)).willReturn(Optional.of(permission(1L, "user:read")));

            permissionService.delete(1L);

            verify(permissionRepository).save(permissionCaptor.capture());
            assertThat(permissionCaptor.getValue().isActive()).isFalse();
        }

        @Test
        @DisplayName("rechaza borrar un permiso incluido en roles con PERMISSION_IN_USE (409)")
        void delete_permissionInUse_shouldThrow() {
            given(permissionRepository.findById(1L)).willReturn(Optional.of(permission(1L, "user:read")));
            given(roleRepository.countActiveByPermissionId(1L)).willReturn(2L);

            assertThatThrownBy(() -> permissionService.delete(1L))
                    .isInstanceOf(PermissionInUseException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.PERMISSION_IN_USE);
            verify(permissionRepository, never()).save(any());
        }
    }
}
