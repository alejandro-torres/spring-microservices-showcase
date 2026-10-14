package com.showcase.auth.infrastructure.persistence.repository.impl;

import com.showcase.auth.domain.repository.PasswordHistoryRepository;
import com.showcase.auth.infrastructure.persistence.entity.PasswordHistoryEntity;
import com.showcase.auth.infrastructure.persistence.repository.PasswordHistoryJpaRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
public class PasswordHistoryRepositoryImpl implements PasswordHistoryRepository {

    private final PasswordHistoryJpaRepository jpaRepository;

    public PasswordHistoryRepositoryImpl(PasswordHistoryJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Long userId, String passwordHash, Instant createdAt) {
        jpaRepository.save(new PasswordHistoryEntity(userId, passwordHash, createdAt));
    }

    @Override
    public List<String> findRecentHashes(Long userId, int limit) {
        return jpaRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(0, limit)).stream()
                .map(PasswordHistoryEntity::getPasswordHash)
                .toList();
    }
}
