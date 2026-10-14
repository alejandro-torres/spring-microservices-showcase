package com.showcase.auth.infrastructure.persistence.repository;

import com.showcase.auth.infrastructure.persistence.entity.UserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserJpaRepository extends JpaRepository<UserEntity, Long>, JpaSpecificationExecutor<UserEntity> {

    /** Carga el usuario con sus roles y permisos en una sola consulta (sin N+1). */
    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<UserEntity> findByIdAndActiveTrue(Long id);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<UserEntity> findByUsernameAndActiveTrue(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    @Query("select count(u) from UserEntity u join u.roles r where r.id = :roleId and u.active = true")
    long countActiveByRoleId(@Param("roleId") Long roleId);
}
