package com.showcase.auth.infrastructure.security;

import static com.showcase.auth.testdata.AuthMother.adminRole;
import static com.showcase.auth.testdata.AuthMother.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.showcase.auth.domain.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class JpaUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private JpaUserDetailsService userDetailsService;

    @Test
    @DisplayName("carga el usuario con ROLE_* y los permisos de sus roles, normalizando el username")
    void loadUserByUsername_shouldMapRolesAndPermissions() {
        given(userRepository.findByUsername("ana")).willReturn(Optional.of(user(10L, "ana", adminRole())));

        var details = userDetailsService.loadUserByUsername("  ANA ");

        assertThat(details.getUsername()).isEqualTo("ana");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "user:read", "user:write");
    }

    @Test
    @DisplayName("marca como deshabilitado al usuario con enabled = false")
    void loadUserByUsername_disabledUser_shouldBeDisabled() {
        var ana = user(10L, "ana");
        ana.update("ana@example.com", false);
        given(userRepository.findByUsername("ana")).willReturn(Optional.of(ana));

        assertThat(userDetailsService.loadUserByUsername("ana").isEnabled()).isFalse();
    }

    @Test
    @DisplayName("no revela si el usuario existe: mismo mensaje genérico")
    void loadUserByUsername_missingUser_shouldThrowGenericMessage() {
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("nadie"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Credenciales no válidas");
    }
}
