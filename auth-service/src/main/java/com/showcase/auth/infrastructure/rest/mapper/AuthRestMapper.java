package com.showcase.auth.infrastructure.rest.mapper;

import com.showcase.auth.domain.model.Permission;
import com.showcase.auth.domain.model.Role;
import com.showcase.auth.domain.model.SecurityAuditEvent;
import com.showcase.auth.domain.model.User;
import com.showcase.auth.infrastructure.rest.dto.response.PermissionResponseDTO;
import com.showcase.auth.infrastructure.rest.dto.response.RoleResponseDTO;
import com.showcase.auth.infrastructure.rest.dto.response.SecurityAuditEventResponseDTO;
import com.showcase.auth.infrastructure.rest.dto.response.UserResponseDTO;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Conversion from domain models to response DTOs. Requests are not mapped to models:
 * the controllers pass their fields to the services, which create the models with their rules.
 */
@Mapper(componentModel = "spring")
public interface AuthRestMapper {

    @Mapping(target = "roles", expression = "java(roleNames(user.getRoles()))")
    UserResponseDTO toResponse(User user);

    @Mapping(target = "permissions", expression = "java(permissionNames(role.getPermissions()))")
    RoleResponseDTO toResponse(Role role);

    PermissionResponseDTO toResponse(Permission permission);

    @Mapping(target = "eventType", source = "type")
    SecurityAuditEventResponseDTO toResponse(SecurityAuditEvent event);

    /** Sorted alphabetically so the response is stable. */
    default Set<String> roleNames(Set<Role> roles) {
        return roles.stream().map(Role::getName).collect(Collectors.toCollection(TreeSet::new));
    }

    default Set<String> permissionNames(Set<Permission> permissions) {
        return permissions.stream().map(Permission::getName).collect(Collectors.toCollection(TreeSet::new));
    }
}
