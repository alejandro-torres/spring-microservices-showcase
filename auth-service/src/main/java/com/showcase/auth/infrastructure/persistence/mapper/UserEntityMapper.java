package com.showcase.auth.infrastructure.persistence.mapper;

import com.showcase.auth.domain.model.User;
import com.showcase.auth.infrastructure.persistence.entity.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", uses = RoleEntityMapper.class)
public interface UserEntityMapper {

    User toDomain(UserEntity entity);

    /** Los roles los asigna el repositorio con referencias a las entidades existentes. */
    @Mapping(target = "roles", ignore = true)
    UserEntity toEntity(User user);

    /** El username y la contraseña no se modifican desde aquí. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "username", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "roles", ignore = true)
    void updateEntity(User user, @MappingTarget UserEntity entity);
}
