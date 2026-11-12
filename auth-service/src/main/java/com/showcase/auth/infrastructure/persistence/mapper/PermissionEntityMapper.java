package com.showcase.auth.infrastructure.persistence.mapper;

import com.showcase.auth.domain.model.Permission;
import com.showcase.auth.infrastructure.persistence.entity.PermissionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface PermissionEntityMapper {

    Permission toDomain(PermissionEntity entity);

    PermissionEntity toEntity(Permission permission);

    /** Copies the modifiable fields onto the managed entity; the id and the name do not change. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "name", ignore = true)
    void updateEntity(Permission permission, @MappingTarget PermissionEntity entity);
}
