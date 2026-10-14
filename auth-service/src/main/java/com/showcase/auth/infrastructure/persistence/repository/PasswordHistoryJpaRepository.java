package com.showcase.auth.infrastructure.persistence.repository;

import com.showcase.auth.infrastructure.persistence.entity.PasswordHistoryEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordHistoryJpaRepository extends JpaRepository<PasswordHistoryEntity, Long> {

    List<PasswordHistoryEntity> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);
}
