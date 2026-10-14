package com.showcase.auth.application.service.impl;

import static com.showcase.auth.testdata.AuthMother.adminRole;
import static com.showcase.auth.testdata.AuthMother.user;
import static com.showcase.auth.testdata.AuthMother.userRole;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.showcase.auth.application.security.PasswordHasher;
import com.showcase.auth.domain.exception.AuthErrorCode;
import com.showcase.auth.domain.exception.EmailAlreadyExistsException;
import com.showcase.auth.domain.exception.RoleNotFoundException;
import com.showcase.auth.domain.exception.UserNotFoundException;
import com.showcase.auth.domain.exception.UsernameAlreadyExistsException;
import com.showcase.auth.domain.model.Role;
import com.showcase.auth.domain.model.User;
import com.showcase.auth.domain.repository.RoleRepository;
import com.showcase.auth.domain.repository.UserRepository;
import com.showcase.common.domain.exception.BusinessRuleException;
import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.ConflictException;
import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.filter.FilterOperator;
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
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @InjectMocks
    private UserServiceImpl userService;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    private void givenSaveReturnsArgument() {
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("normaliza los datos, cifra la contraseña y guarda el usuario con sus roles")
        void create_shouldHashPasswordAndSave() {
            // given
            given(roleRepository.findAllById(Set.of(2L))).willReturn(List.of(userRole()));
            given(passwordHasher.hash("Secreta-123")).willReturn("{bcrypt}cifrada");
            givenSaveReturnsArgument();

            // when
            var created = userService.create(" Ana ", "ANA@example.com", "Secreta-123", Set.of(2L));

            // then
            verify(userRepository).save(userCaptor.capture());
            var saved = userCaptor.getValue();
            assertThat(saved.getUsername()).isEqualTo("ana");
            assertThat(saved.getEmail()).isEqualTo("ana@example.com");
            assertThat(saved.getPasswordHash()).isEqualTo("{bcrypt}cifrada");
            assertThat(saved.getRoles()).extracting(Role::getName).containsExactly("USER");
            assertThat(created).isSameAs(saved);
        }

        @Test
        @DisplayName("rechaza un username ya existente sin cifrar ni guardar nada")
        void create_withExistingUsername_shouldThrow() {
            given(userRepository.existsByUsername("ana")).willReturn(true);

            assertThatThrownBy(() -> userService.create("ana", "ana@example.com", "Secreta-123", Set.of()))
                    .isInstanceOf(UsernameAlreadyExistsException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.USERNAME_ALREADY_EXISTS);
            verify(passwordHasher, never()).hash(anyString());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("rechaza un email ya existente")
        void create_withExistingEmail_shouldThrow() {
            given(userRepository.existsByEmail("ana@example.com")).willReturn(true);

            assertThatThrownBy(() -> userService.create("ana", "ana@example.com", "Secreta-123", Set.of()))
                    .isInstanceOf(EmailAlreadyExistsException.class);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("rechaza roles inexistentes indicando cuáles faltan")
        void create_withMissingRoles_shouldThrowWithMissingIds() {
            given(roleRepository.findAllById(Set.of(2L, 7L, 9L))).willReturn(List.of(userRole()));

            assertThatThrownBy(() -> userService.create("ana", "ana@example.com", "Secreta-123", Set.of(2L, 7L, 9L)))
                    .isInstanceOf(RoleNotFoundException.class)
                    .satisfies(e -> assertThat(((RoleNotFoundException) e).getArgs()).containsExactly("7, 9"));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("permite crear un usuario sin roles")
        void create_withoutRoles_shouldNotQueryRoles() {
            given(passwordHasher.hash(anyString())).willReturn("{bcrypt}cifrada");
            givenSaveReturnsArgument();

            var created = userService.create("ana", "ana@example.com", "Secreta-123", null);

            assertThat(created.getRoles()).isEmpty();
            verify(roleRepository, never()).findAllById(any());
        }
    }

    @Nested
    @DisplayName("findById y findAll")
    class Find {

        @Test
        @DisplayName("devuelve el usuario o lanza USER_NOT_FOUND")
        void findById_shouldReturnOrThrow() {
            var ana = user(10L, "ana");
            given(userRepository.findById(10L)).willReturn(Optional.of(ana));

            assertThat(userService.findById(10L)).isSameAs(ana);
            assertThatThrownBy(() -> userService.findById(99L))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("pasa los filtros y la paginación tal cual al repositorio")
        void findAll_shouldDelegateToRepository() {
            var filters = List.of(FilterCriterion.of("role", FilterOperator.EQ, "ADMIN"));
            var query = PageQuery.firstPage();
            var page = PageResult.of(List.of(user(10L, "ana")), 0, 10, 1);
            given(userRepository.findAll(filters, query, true)).willReturn(page);

            assertThat(userService.findAll(filters, query, true)).isSameAs(page);
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("actualiza email y estado cuando la versión coincide")
        void update_withCurrentVersion_shouldSave() {
            given(userRepository.findById(10L)).willReturn(Optional.of(user(10L, "ana")));
            givenSaveReturnsArgument();

            var updated = userService.update(10L, "Nueva@Example.com", false, 0L);

            assertThat(updated.getEmail()).isEqualTo("nueva@example.com");
            assertThat(updated.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("rechaza una versión desactualizada con CONCURRENT_MODIFICATION y no guarda")
        void update_withStaleVersion_shouldThrowConflict() {
            given(userRepository.findById(10L)).willReturn(Optional.of(user(10L, "ana")));

            assertThatThrownBy(() -> userService.update(10L, "nueva@example.com", true, 3L))
                    .isInstanceOf(ConflictException.class)
                    .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.CONCURRENT_MODIFICATION);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("rechaza un email que ya usa otro usuario")
        void update_withEmailOfAnotherUser_shouldThrow() {
            given(userRepository.findById(10L)).willReturn(Optional.of(user(10L, "ana")));
            given(userRepository.existsByEmailAndIdNot("bea@example.com", 10L)).willReturn(true);

            assertThatThrownBy(() -> userService.update(10L, "bea@example.com", true, 0L))
                    .isInstanceOf(EmailAlreadyExistsException.class);
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("hace un borrado lógico: desactiva y deshabilita el usuario")
        void delete_shouldDeactivate() {
            given(userRepository.findById(10L)).willReturn(Optional.of(user(10L, "ana")));

            userService.delete(10L);

            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().isActive()).isFalse();
            assertThat(userCaptor.getValue().isEnabled()).isFalse();
        }

        @Test
        @DisplayName("lanza USER_NOT_FOUND si el usuario no existe o ya está borrado")
        void delete_missingUser_shouldThrow() {
            assertThatThrownBy(() -> userService.delete(10L)).isInstanceOf(UserNotFoundException.class);
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("assignRole y removeRole")
    class RolesManagement {

        @Test
        @DisplayName("asigna un rol existente")
        void assignRole_shouldAddRole() {
            given(userRepository.findById(10L)).willReturn(Optional.of(user(10L, "ana", userRole())));
            given(roleRepository.findById(1L)).willReturn(Optional.of(adminRole()));
            givenSaveReturnsArgument();

            var updated = userService.assignRole(10L, 1L);

            assertThat(updated.getRoles()).extracting(Role::getName).containsExactlyInAnyOrder("USER", "ADMIN");
        }

        @Test
        @DisplayName("lanza ROLE_NOT_FOUND si el rol no existe")
        void assignRole_missingRole_shouldThrow() {
            given(userRepository.findById(10L)).willReturn(Optional.of(user(10L, "ana")));

            assertThatThrownBy(() -> userService.assignRole(10L, 1L)).isInstanceOf(RoleNotFoundException.class);
        }

        @Test
        @DisplayName("quita un rol asignado y rechaza quitar uno que no tiene")
        void removeRole_shouldRemoveOrThrow() {
            given(userRepository.findById(10L)).willReturn(Optional.of(user(10L, "ana", userRole(), adminRole())));
            givenSaveReturnsArgument();

            assertThat(userService.removeRole(10L, 1L).getRoles()).extracting(Role::getName).containsExactly("USER");
            assertThatThrownBy(() -> userService.removeRole(10L, 1L))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.ROLE_NOT_ASSIGNED);
        }
    }
}
