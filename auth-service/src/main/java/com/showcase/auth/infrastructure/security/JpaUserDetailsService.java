package com.showcase.auth.infrastructure.security;

import com.showcase.auth.domain.model.Role;
import com.showcase.auth.domain.model.User;
import com.showcase.auth.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador entre el login de Spring Security y el dominio: carga el usuario con sus roles
 * ({@code ROLE_*}) y los permisos de esos roles como autoridades.
 */
@Service
@RequiredArgsConstructor
public class JpaUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase(java.util.Locale.ROOT);
        User user = userRepository.findByUsername(normalized)
                // Mismo mensaje para cualquier fallo de login: no se revela si el usuario existe.
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales no válidas"));
        return org.springframework.security.core.userdetails.User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .disabled(!user.isEnabled())
                // Spring Security comprueba el bloqueo antes que la contraseña (LockedException).
                .accountLocked(user.isLocked(Instant.now(clock)))
                .authorities(authorities(user))
                .build();
    }

    private static Set<GrantedAuthority> authorities(User user) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        for (Role role : user.getRoles()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getName()));
            role.permissionNames().forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
        }
        return authorities;
    }
}
