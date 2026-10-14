package com.showcase.auth.application.service.impl;

import com.showcase.auth.application.security.PasswordHasher;
import com.showcase.auth.application.service.UserService;
import com.showcase.auth.domain.exception.EmailAlreadyExistsException;
import com.showcase.auth.domain.exception.RoleNotFoundException;
import com.showcase.auth.domain.exception.UserNotFoundException;
import com.showcase.auth.domain.exception.UsernameAlreadyExistsException;
import com.showcase.auth.domain.model.Role;
import com.showcase.auth.domain.model.User;
import com.showcase.auth.domain.repository.RoleRepository;
import com.showcase.auth.domain.repository.UserRepository;
import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las lecturas van en transacciones de solo lectura (readOnly a nivel de clase) y cada escritura
 * abre su propia transacción: si algo falla a mitad, se deshace todo.
 */
@Service
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordHasher passwordHasher;

    public UserServiceImpl(UserRepository userRepository, RoleRepository roleRepository,
                           PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordHasher = passwordHasher;
    }

    @Override
    @Transactional
    public User create(String username, String email, String rawPassword, Set<Long> roleIds) {
        String normalizedUsername = User.normalizeUsername(username);
        String normalizedEmail = User.normalizeEmail(email);
        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new UsernameAlreadyExistsException(normalizedUsername);
        }
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException(normalizedEmail);
        }
        Set<Role> roles = findRoles(roleIds);
        User user = User.create(normalizedUsername, normalizedEmail, passwordHasher.hash(rawPassword), roles);
        return userRepository.save(user);
    }

    @Override
    public User findById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    @Override
    public PageResult<User> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive) {
        return userRepository.findAll(filters, pageQuery, includeInactive);
    }

    @Override
    @Transactional
    public User update(Long id, String email, boolean enabled, Long version) {
        User user = findById(id);
        user.checkVersion(version);
        String normalizedEmail = User.normalizeEmail(email);
        if (userRepository.existsByEmailAndIdNot(normalizedEmail, id)) {
            throw new EmailAlreadyExistsException(normalizedEmail);
        }
        user.update(normalizedEmail, enabled);
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        User user = findById(id);
        user.deactivate();
        userRepository.save(user);
    }

    @Override
    @Transactional
    public User assignRole(Long userId, Long roleId) {
        User user = findById(userId);
        Role role = roleRepository.findById(roleId).orElseThrow(() -> new RoleNotFoundException(roleId));
        user.assignRole(role);
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public User removeRole(Long userId, Long roleId) {
        User user = findById(userId);
        user.removeRole(roleId);
        return userRepository.save(user);
    }

    // Todos los roles pedidos tienen que existir y estar activos; si falta alguno se indica cuál.
    private Set<Role> findRoles(Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Set.of();
        }
        List<Role> roles = roleRepository.findAllById(roleIds);
        if (roles.size() != roleIds.size()) {
            Set<Long> missing = new HashSet<>(roleIds);
            roles.forEach(role -> missing.remove(role.getId()));
            throw new RoleNotFoundException(missing.stream().sorted().map(String::valueOf)
                    .collect(Collectors.joining(", ")));
        }
        return new HashSet<>(roles);
    }
}
