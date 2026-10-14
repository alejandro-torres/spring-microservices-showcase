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

    /** Copia los campos modificables sobre la entidad gestionada; el id y el nombre no cambian. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "name", ignore = true)
    void updateEntity(Permission permission, @MappingTarget PermissionEntity entity);
}
