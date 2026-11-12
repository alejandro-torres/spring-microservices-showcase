package com.showcase.auth.infrastructure.persistence.mapper;

import com.showcase.auth.domain.model.User;
import com.showcase.auth.infrastructure.persistence.entity.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", uses = RoleEntityMapper.class)
public interface UserEntityMapper {

    User toDomain(UserEntity entity);

    /** The repository assigns the roles with references to the existing entities. */
    @Mapping(target = "roles", ignore = true)
    UserEntity toEntity(User user);

    /** The username never changes; the password arrives already hashed from the domain. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "username", ignore = true)
    @Mapping(target = "roles", ignore = true)
    void updateEntity(User user, @MappingTarget UserEntity entity);
}
