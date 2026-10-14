package com.showcase.auth.infrastructure.persistence.mapper;

import com.showcase.auth.domain.model.Role;
import com.showcase.auth.infrastructure.persistence.entity.RoleEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", uses = PermissionEntityMapper.class)
public interface RoleEntityMapper {

    Role toDomain(RoleEntity entity);

    /** Los permisos los asigna el repositorio con referencias a las entidades existentes. */
    @Mapping(target = "permissions", ignore = true)
    RoleEntity toEntity(Role role);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "name", ignore = true)
    @Mapping(target = "permissions", ignore = true)
    void updateEntity(Role role, @MappingTarget RoleEntity entity);
}
