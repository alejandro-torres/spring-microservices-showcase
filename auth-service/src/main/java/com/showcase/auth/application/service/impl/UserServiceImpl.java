package com.showcase.auth.application.service.impl;

import com.showcase.auth.application.security.PasswordHasher;
import com.showcase.auth.application.service.SecurityAuditService;
import com.showcase.auth.application.service.UserService;
import com.showcase.auth.domain.exception.EmailAlreadyExistsException;
import com.showcase.auth.domain.exception.RoleNotFoundException;
import com.showcase.auth.domain.exception.UserNotFoundException;
import com.showcase.auth.domain.exception.UsernameAlreadyExistsException;
import com.showcase.auth.domain.model.Role;
import com.showcase.auth.domain.model.SecurityEventType;
import com.showcase.auth.domain.model.User;
import com.showcase.auth.domain.repository.RoleRepository;
import com.showcase.auth.domain.repository.UserRepository;
import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
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

    static final String DEFAULT_ROLE = "USER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordHasher passwordHasher;
    private final SecurityAuditService auditService;

    public UserServiceImpl(UserRepository userRepository, RoleRepository roleRepository,
                           PasswordHasher passwordHasher, SecurityAuditService auditService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordHasher = passwordHasher;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public User create(String username, String email, String rawPassword, Set<Long> roleIds) {
        return createUser(username, email, rawPassword, () -> findRoles(roleIds));
    }

    @Override
    @Transactional
    public User register(String username, String email, String rawPassword) {
        return createUser(username, email, rawPassword, () -> Set.of(roleRepository.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new RoleNotFoundException(DEFAULT_ROLE))));
    }

    @Override
    public User findById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    @Override
    public User findByUsername(String username) {
        return userRepository.findByUsername(User.normalizeUsername(username))
                .orElseThrow(() -> new UserNotFoundException(username));
    }

    // Las comprobaciones de unicidad van antes de resolver los roles y de cifrar la contraseña (BCrypt es lento).
    private User createUser(String username, String email, String rawPassword, Supplier<Set<Role>> roles) {
        String normalizedUsername = User.normalizeUsername(username);
        String normalizedEmail = User.normalizeEmail(email);
        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new UsernameAlreadyExistsException(normalizedUsername);
        }
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException(normalizedEmail);
        }
        User user = User.create(normalizedUsername, normalizedEmail, passwordHasher.hash(rawPassword), roles.get());
        return userRepository.save(user);
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
        boolean wasEnabled = user.isEnabled();
        user.update(normalizedEmail, enabled);
        User saved = userRepository.save(user);
        if (wasEnabled && !enabled) {
            auditService.record(SecurityEventType.USER_DISABLED, saved.getUsername(), "enabled=false");
        }
        return saved;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        User user = findById(id);
        user.deactivate();
        userRepository.save(user);
        auditService.record(SecurityEventType.USER_DISABLED, user.getUsername(), "deleted");
    }

    @Override
    @Transactional
    public User assignRole(Long userId, Long roleId) {
        User user = findById(userId);
        Role role = roleRepository.findById(roleId).orElseThrow(() -> new RoleNotFoundException(roleId));
        user.assignRole(role);
        User saved = userRepository.save(user);
        auditService.record(SecurityEventType.ROLE_ASSIGNED, saved.getUsername(), "role=" + role.getName());
        return saved;
    }

    @Override
    @Transactional
    public User removeRole(Long userId, Long roleId) {
        User user = findById(userId);
        String roleName = user.getRoles().stream().filter(role -> roleId.equals(role.getId()))
                .map(Role::getName).findFirst().orElse(String.valueOf(roleId));
        user.removeRole(roleId);
        User saved = userRepository.save(user);
        auditService.record(SecurityEventType.ROLE_REMOVED, saved.getUsername(), "role=" + roleName);
        return saved;
    }

    @Override
    @Transactional
    public User unlock(Long id) {
        User user = findById(id);
        user.unlock();
        User saved = userRepository.save(user);
        auditService.record(SecurityEventType.ACCOUNT_UNLOCKED, saved.getUsername(), null);
        return saved;
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
