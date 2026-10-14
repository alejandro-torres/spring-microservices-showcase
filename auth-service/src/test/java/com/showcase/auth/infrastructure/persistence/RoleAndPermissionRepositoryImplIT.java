package com.showcase.auth.infrastructure.persistence;

import static com.showcase.common.domain.filter.FilterOperator.EQ;
import static com.showcase.common.domain.filter.FilterOperator.STARTS_WITH;
import static org.assertj.core.api.Assertions.assertThat;

import com.showcase.auth.domain.model.Permission;
import com.showcase.auth.domain.model.Role;
import com.showcase.auth.domain.repository.PermissionRepository;
import com.showcase.auth.domain.repository.RoleRepository;
import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.SortDirection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RoleAndPermissionRepositoryImplIT extends AbstractPersistenceIT {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    private List<Permission> permissions(String prefix) {
        return permissionRepository.findAll(List.of(FilterCriterion.of("name", STARTS_WITH, prefix)),
                new PageQuery(0, 100, "name", SortDirection.ASC), false).content();
    }

    @Nested
    @DisplayName("permisos")
    class Permissions {

        @Test
        @DisplayName("la migración crea los 12 permisos base y se pueden filtrar por prefijo")
        void baseData_shouldContainPermissions() {
            assertThat(permissionRepository.findAll(List.of(), new PageQuery(0, 100, "id", SortDirection.ASC), false)
                    .totalElements()).isEqualTo(12);
            assertThat(permissions("user:")).extracting(Permission::getName).containsExactly("user:read", "user:write");
        }

        @Test
        @DisplayName("guarda, actualiza la descripción y busca por ids ignorando los inactivos")
        void saveUpdateAndFindAllById_shouldWork() {
            var created = permissionRepository.save(Permission.create("report:read", "Ver informes"));
            created.updateDescription("Consultar informes");
            var updated = permissionRepository.save(created);
            var inactive = permissionRepository.save(Permission.create("report:write", null));
            inactive.deactivate();
            permissionRepository.save(inactive);

            assertThat(updated.getVersion()).isEqualTo(1L);
            assertThat(permissionRepository.findAllById(Set.of(created.getId(), inactive.getId(), 9999L)))
                    .extracting(Permission::getName).containsExactly("report:read");
            assertThat(permissionRepository.existsByName("report:read")).isTrue();
        }
    }

    @Nested
    @DisplayName("roles")
    class Roles {

        @Test
        @DisplayName("guarda un rol con permisos y filtra roles por permiso")
        void saveAndFilterByPermission_shouldWork() {
            // given
            var support = roleRepository.save(Role.create("SUPPORT", "Soporte", new HashSet<>(permissions("customer:"))));

            // when
            var withCustomerWrite = roleRepository.findAll(
                    List.of(FilterCriterion.of("permission", EQ, "customer:write")),
                    new PageQuery(0, 10, "name", SortDirection.ASC), false);

            // then
            assertThat(support.permissionNames()).containsExactlyInAnyOrder("customer:read", "customer:write");
            assertThat(withCustomerWrite.content()).extracting(Role::getName).containsExactly("ADMIN", "SUPPORT");
        }

        @Test
        @DisplayName("actualiza los permisos de un rol e incrementa la versión")
        void update_shouldReplacePermissions() {
            var support = roleRepository.save(Role.create("SUPPORT", null, new HashSet<>(permissions("customer:"))));

            support.update("Soporte de pedidos", new HashSet<>(permissions("order:")));
            var updated = roleRepository.save(support);

            assertThat(updated.getVersion()).isEqualTo(1L);
            assertThat(updated.permissionNames()).containsExactlyInAnyOrder("order:read", "order:write");
        }

        @Test
        @DisplayName("cuenta los roles activos que incluyen un permiso")
        void countActiveByPermissionId_shouldCountActiveRoles() {
            var orderRead = permissions("order:read").getFirst();
            var support = roleRepository.save(Role.create("SUPPORT", null, Set.of(orderRead)));
            support.deactivate();
            roleRepository.save(support);

            // ADMIN y USER lo tienen; SUPPORT está borrado
            assertThat(roleRepository.countActiveByPermissionId(orderRead.getId())).isEqualTo(2);
        }
    }
}
