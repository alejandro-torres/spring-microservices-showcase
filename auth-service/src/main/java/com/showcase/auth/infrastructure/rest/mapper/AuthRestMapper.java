package com.showcase.auth.infrastructure.rest.mapper;

import com.showcase.auth.domain.model.Permission;
import com.showcase.auth.domain.model.Role;
import com.showcase.auth.domain.model.User;
import com.showcase.auth.infrastructure.rest.dto.response.PermissionResponseDTO;
import com.showcase.auth.infrastructure.rest.dto.response.RoleResponseDTO;
import com.showcase.auth.infrastructure.rest.dto.response.UserResponseDTO;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Conversión de los modelos de dominio a los DTOs de respuesta. Las peticiones no se mapean a modelos:
 * los controllers pasan sus campos a los servicios, que crean los modelos con sus reglas.
 */
@Mapper(componentModel = "spring")
public interface AuthRestMapper {

    @Mapping(target = "roles", expression = "java(roleNames(user.getRoles()))")
    UserResponseDTO toResponse(User user);

    @Mapping(target = "permissions", expression = "java(permissionNames(role.getPermissions()))")
    RoleResponseDTO toResponse(Role role);

    PermissionResponseDTO toResponse(Permission permission);

    /** Ordenados alfabéticamente para que la respuesta sea estable. */
    default Set<String> roleNames(Set<Role> roles) {
        return roles.stream().map(Role::getName).collect(Collectors.toCollection(TreeSet::new));
    }

    default Set<String> permissionNames(Set<Permission> permissions) {
        return permissions.stream().map(Permission::getName).collect(Collectors.toCollection(TreeSet::new));
    }
}
