package com.showcase.auth.infrastructure.persistence.repository;

import com.showcase.auth.infrastructure.persistence.entity.PermissionEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PermissionJpaRepository
        extends JpaRepository<PermissionEntity, Long>, JpaSpecificationExecutor<PermissionEntity> {

    Optional<PermissionEntity> findByIdAndActiveTrue(Long id);

    List<PermissionEntity> findAllByIdInAndActiveTrue(Collection<Long> ids);

    boolean existsByName(String name);
}
