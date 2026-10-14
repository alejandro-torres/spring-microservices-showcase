package com.showcase.auth.infrastructure.persistence;

import static com.showcase.common.domain.filter.FilterOperator.EQ;
import static com.showcase.common.domain.filter.FilterOperator.LIKE;
import static org.assertj.core.api.Assertions.assertThat;

import com.showcase.auth.domain.model.Role;
import com.showcase.auth.domain.model.User;
import com.showcase.auth.domain.repository.RoleRepository;
import com.showcase.auth.domain.repository.UserRepository;
import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.SortDirection;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UserRepositoryImplIT extends AbstractPersistenceIT {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EntityManager entityManager;

    private Role admin;
    private Role user;

    @BeforeEach
    void loadBaseRoles() {
        // Los roles ADMIN y USER los crea la migración V3.
        var roles = roleRepository.findAll(List.of(), PageQuery.firstPage(), false).content();
        admin = roles.stream().filter(r -> r.getName().equals("ADMIN")).findFirst().orElseThrow();
        user = roles.stream().filter(r -> r.getName().equals("USER")).findFirst().orElseThrow();
    }

    private User saveUser(String username, Role... roles) {
        return userRepository.save(User.create(username, username + "@example.com", "{bcrypt}hash", Set.of(roles)));
    }

    private void clearPersistenceContext() {
        entityManager.flush();
        entityManager.clear();
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("guarda un usuario nuevo con id, versión 0, fechas de auditoría y roles")
        void save_newUser_shouldAssignIdVersionAndAudit() {
            // when
            var saved = saveUser("ana", user);

            // then
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getVersion()).isZero();
            assertThat(saved.getCreatedAt()).isEqualTo(NOW);
            assertThat(saved.getUpdatedAt()).isEqualTo(NOW);
            assertThat(saved.getRoles()).extracting(Role::getName).containsExactly("USER");
        }

        @Test
        @DisplayName("actualiza un usuario existente e incrementa la versión")
        void save_existingUser_shouldIncrementVersion() {
            // given
            var saved = saveUser("ana", user);
            clearPersistenceContext();
            var loaded = userRepository.findById(saved.getId()).orElseThrow();

            // when
            loaded.update("ana.nueva@example.com", false);
            loaded.assignRole(admin);
            var updated = userRepository.save(loaded);

            // then
            assertThat(updated.getVersion()).isEqualTo(1L);
            assertThat(updated.getEmail()).isEqualTo("ana.nueva@example.com");
            assertThat(updated.isEnabled()).isFalse();
            assertThat(updated.getRoles()).extracting(Role::getName).containsExactlyInAnyOrder("USER", "ADMIN");
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("devuelve el usuario con sus roles y los permisos de cada rol")
        void findById_shouldLoadRolesAndPermissions() {
            var saved = saveUser("ana", admin);
            clearPersistenceContext();

            var found = userRepository.findById(saved.getId()).orElseThrow();

            assertThat(found.getRoles()).singleElement()
                    .satisfies(role -> assertThat(role.permissionNames()).contains("user:read", "order:write"));
        }

        @Test
        @DisplayName("no devuelve usuarios borrados (borrado lógico)")
        void findById_inactiveUser_shouldReturnEmpty() {
            var saved = saveUser("ana", user);
            saved.deactivate();
            userRepository.save(saved);
            clearPersistenceContext();

            assertThat(userRepository.findById(saved.getId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAll con paginación y filtros")
    class FindAll {

        @BeforeEach
        void createUsers() {
            for (int i = 1; i <= 12; i++) {
                saveUser("user%02d".formatted(i), user);
            }
            saveUser("ana.admin", admin, user);
            saveUser("bea.admin", admin);
            var deleted = saveUser("deleted.admin", admin);
            deleted.deactivate();
            userRepository.save(deleted);
            clearPersistenceContext();
        }

        @Test
        @DisplayName("pagina con totales y ordenación")
        void findAll_shouldPaginateAndSort() {
            var page = userRepository.findAll(List.of(), new PageQuery(1, 5, "username", SortDirection.ASC), false);

            assertThat(page.totalElements()).isEqualTo(14);
            assertThat(page.totalPages()).isEqualTo(3);
            assertThat(page.content()).extracting(User::getUsername)
                    .containsExactly("user04", "user05", "user06", "user07", "user08");
        }

        @Test
        @DisplayName("filtra por el nombre del rol (join con la colección) sin duplicar usuarios")
        void findAll_byRole_shouldNotDuplicate() {
            var page = userRepository.findAll(List.of(FilterCriterion.of("role", EQ, "ADMIN")),
                    new PageQuery(0, 10, "username", SortDirection.ASC), false);

            assertThat(page.content()).extracting(User::getUsername).containsExactly("ana.admin", "bea.admin");
            assertThat(page.totalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("combina filtros y puede incluir los usuarios borrados")
        void findAll_withSeveralFiltersAndInactive_shouldWork() {
            var filters = List.of(FilterCriterion.of("username", LIKE, "ADMIN"), FilterCriterion.of("role", EQ, "ADMIN"));

            assertThat(userRepository.findAll(filters, PageQuery.firstPage(), false).totalElements()).isEqualTo(2);
            assertThat(userRepository.findAll(filters, PageQuery.firstPage(), true).totalElements()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("consultas de existencia y recuento")
    class ExistsAndCount {

        @Test
        @DisplayName("comprueba username y email, excluyendo al propio usuario")
        void exists_shouldCheckUsernameAndEmail() {
            var saved = saveUser("ana", user);

            assertThat(userRepository.existsByUsername("ana")).isTrue();
            assertThat(userRepository.existsByEmail("ana@example.com")).isTrue();
            assertThat(userRepository.existsByEmailAndIdNot("ana@example.com", saved.getId())).isFalse();
            assertThat(userRepository.existsByUsername("otro")).isFalse();
        }

        @Test
        @DisplayName("cuenta solo los usuarios activos con un rol")
        void countActiveByRoleId_shouldIgnoreInactive() {
            saveUser("ana", admin);
            var deleted = saveUser("bea", admin);
            deleted.deactivate();
            userRepository.save(deleted);

            assertThat(userRepository.countActiveByRoleId(admin.getId())).isEqualTo(1);
        }
    }
}
