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
        // The ADMIN and USER roles are created by migration V3.
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
        @DisplayName("saves a new user with id, version 0, audit dates and roles")
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
        @DisplayName("updates an existing user and increments the version")
        void save_existingUser_shouldIncrementVersion() {
            // given
            var saved = saveUser("ana", user);
            clearPersistenceContext();
            var loaded = userRepository.findById(saved.getId()).orElseThrow();

            // when
            loaded.update("ana.new@example.com", false);
            loaded.assignRole(admin);
            var updated = userRepository.save(loaded);

            // then
            assertThat(updated.getVersion()).isEqualTo(1L);
            assertThat(updated.getEmail()).isEqualTo("ana.new@example.com");
            assertThat(updated.isEnabled()).isFalse();
            assertThat(updated.getRoles()).extracting(Role::getName).containsExactlyInAnyOrder("USER", "ADMIN");
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("returns the user with their roles and each role's permissions")
        void findById_shouldLoadRolesAndPermissions() {
            var saved = saveUser("ana", admin);
            clearPersistenceContext();

            var found = userRepository.findById(saved.getId()).orElseThrow();

            assertThat(found.getRoles()).singleElement()
                    .satisfies(role -> assertThat(role.permissionNames()).contains("user:read", "order:write"));
        }

        @Test
        @DisplayName("does not return deleted users (soft delete)")
        void findById_inactiveUser_shouldReturnEmpty() {
            var saved = saveUser("ana", user);
            saved.deactivate();
            userRepository.save(saved);
            clearPersistenceContext();

            assertThat(userRepository.findById(saved.getId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAll with pagination and filters")
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
        @DisplayName("paginates with totals and sorting")
        void findAll_shouldPaginateAndSort() {
            var page = userRepository.findAll(List.of(), new PageQuery(1, 5, "username", SortDirection.ASC), false);

            assertThat(page.totalElements()).isEqualTo(14);
            assertThat(page.totalPages()).isEqualTo(3);
            assertThat(page.content()).extracting(User::getUsername)
                    .containsExactly("user04", "user05", "user06", "user07", "user08");
        }

        @Test
        @DisplayName("filters by role name (join with the collection) without duplicating users")
        void findAll_byRole_shouldNotDuplicate() {
            var page = userRepository.findAll(List.of(FilterCriterion.of("role", EQ, "ADMIN")),
                    new PageQuery(0, 10, "username", SortDirection.ASC), false);

            assertThat(page.content()).extracting(User::getUsername).containsExactly("ana.admin", "bea.admin");
            assertThat(page.totalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("combines filters and can include deleted users")
        void findAll_withSeveralFiltersAndInactive_shouldWork() {
            var filters = List.of(FilterCriterion.of("username", LIKE, "ADMIN"), FilterCriterion.of("role", EQ, "ADMIN"));

            assertThat(userRepository.findAll(filters, PageQuery.firstPage(), false).totalElements()).isEqualTo(2);
            assertThat(userRepository.findAll(filters, PageQuery.firstPage(), true).totalElements()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("existence and count queries")
    class ExistsAndCount {

        @Test
        @DisplayName("checks username and email, excluding the user themselves")
        void exists_shouldCheckUsernameAndEmail() {
            var saved = saveUser("ana", user);

            assertThat(userRepository.existsByUsername("ana")).isTrue();
            assertThat(userRepository.existsByEmail("ana@example.com")).isTrue();
            assertThat(userRepository.existsByEmailAndIdNot("ana@example.com", saved.getId())).isFalse();
            assertThat(userRepository.existsByUsername("other")).isFalse();
        }

        @Test
        @DisplayName("counts only the active users with a role")
        void countActiveByRoleId_shouldIgnoreInactive() {
            saveUser("ana", admin);
            var deleted = saveUser("bea", admin);
            deleted.deactivate();
            userRepository.save(deleted);

            assertThat(userRepository.countActiveByRoleId(admin.getId())).isEqualTo(1);
        }
    }
}
