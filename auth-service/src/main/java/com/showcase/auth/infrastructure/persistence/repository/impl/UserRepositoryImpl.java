package com.showcase.auth.infrastructure.persistence.repository.impl;

import static com.showcase.auth.infrastructure.persistence.specification.AuthFilterMappings.USERS;

import com.showcase.auth.domain.exception.UserNotFoundException;
import com.showcase.auth.domain.model.Role;
import com.showcase.auth.domain.model.User;
import com.showcase.auth.domain.repository.UserRepository;
import com.showcase.auth.infrastructure.persistence.entity.RoleEntity;
import com.showcase.auth.infrastructure.persistence.entity.UserEntity;
import com.showcase.auth.infrastructure.persistence.mapper.UserEntityMapper;
import com.showcase.auth.infrastructure.persistence.repository.RoleJpaRepository;
import com.showcase.auth.infrastructure.persistence.repository.UserJpaRepository;
import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.common.infrastructure.persistence.page.SpringDataPageAdapter;
import com.showcase.common.infrastructure.persistence.specification.GenericSpecificationBuilder;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

/**
 * Persistence adapter for users: implements the domain port with Spring Data JPA.
 * It opens no transactions of its own; it joins the service's.
 */
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository userJpaRepository;
    private final RoleJpaRepository roleJpaRepository;
    private final UserEntityMapper mapper;
    private final GenericSpecificationBuilder specificationBuilder;

    @Override
    public User save(User user) {
        UserEntity entity;
        if (user.getId() == null) {
            entity = mapper.toEntity(user);
        } else {
            entity = userJpaRepository.findById(user.getId()).orElseThrow(() -> new UserNotFoundException(user.getId()));
            mapper.updateEntity(user, entity);
        }
        entity.setRoles(roleReferences(user.getRoles()));
        // saveAndFlush to return the version and modification date already updated.
        return mapper.toDomain(userJpaRepository.saveAndFlush(entity));
    }

    @Override
    public Optional<User> findById(Long id) {
        return userJpaRepository.findByIdAndActiveTrue(id).map(mapper::toDomain);
    }

    @Override
    public List<User> findWithExpiredLock(Instant now, int limit) {
        return userJpaRepository.findByActiveTrueAndLockedUntilLessThanEqualOrderById(now, Limit.of(limit)).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return userJpaRepository.findByUsernameAndActiveTrue(username).map(mapper::toDomain);
    }

    @Override
    public PageResult<User> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive) {
        Page<UserEntity> page = userJpaRepository.findAll(
                specificationBuilder.build(filters, USERS, includeInactive),
                SpringDataPageAdapter.toPageable(pageQuery, USERS));
        return SpringDataPageAdapter.toPageResult(page, mapper::toDomain);
    }

    @Override
    public boolean existsByUsername(String username) {
        return userJpaRepository.existsByUsername(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userJpaRepository.existsByEmail(email);
    }

    @Override
    public boolean existsByEmailAndIdNot(String email, Long id) {
        return userJpaRepository.existsByEmailAndIdNot(email, id);
    }

    @Override
    public long countActiveByRoleId(Long roleId) {
        return userJpaRepository.countActiveByRoleId(roleId);
    }

    // References (no SELECT) to roles the service has already checked exist.
    private Set<RoleEntity> roleReferences(Set<Role> roles) {
        return roles.stream()
                .map(role -> roleJpaRepository.getReferenceById(role.getId()))
                .collect(Collectors.toCollection(HashSet::new));
    }
}
