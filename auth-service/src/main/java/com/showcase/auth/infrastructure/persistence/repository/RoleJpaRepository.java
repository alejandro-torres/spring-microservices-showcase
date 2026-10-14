package com.showcase.auth.infrastructure.persistence.repository;

import com.showcase.auth.infrastructure.persistence.entity.RoleEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleJpaRepository extends JpaRepository<RoleEntity, Long>, JpaSpecificationExecutor<RoleEntity> {

    @EntityGraph(attributePaths = "permissions")
    Optional<RoleEntity> findByIdAndActiveTrue(Long id);

    @EntityGraph(attributePaths = "permissions")
    Optional<RoleEntity> findByNameAndActiveTrue(String name);

    @EntityGraph(attributePaths = "permissions")
    List<RoleEntity> findAllByIdInAndActiveTrue(Collection<Long> ids);

    boolean existsByName(String name);

    @Query("select count(r) from RoleEntity r join r.permissions p where p.id = :permissionId and r.active = true")
    long countActiveByPermissionId(@Param("permissionId") Long permissionId);
}
