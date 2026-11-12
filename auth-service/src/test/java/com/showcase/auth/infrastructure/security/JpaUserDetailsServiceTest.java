package com.showcase.auth.infrastructure.security;

import static com.showcase.auth.testdata.AuthMother.adminRole;
import static com.showcase.auth.testdata.AuthMother.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.showcase.auth.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class JpaUserDetailsServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-14T12:00:00Z");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final JpaUserDetailsService userDetailsService =
            new JpaUserDetailsService(userRepository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("loads the user with ROLE_* and their roles' permissions, normalizing the username")
    void loadUserByUsername_shouldMapRolesAndPermissions() {
        given(userRepository.findByUsername("ana")).willReturn(Optional.of(user(10L, "ana", adminRole())));

        var details = userDetailsService.loadUserByUsername("  ANA ");

        assertThat(details.getUsername()).isEqualTo("ana");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
        assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "user:read", "user:write");
    }

    @Test
    @DisplayName("marks the user as disabled with enabled = false")
    void loadUserByUsername_disabledUser_shouldBeDisabled() {
        var ana = user(10L, "ana");
        ana.update("ana@example.com", false);
        given(userRepository.findByUsername("ana")).willReturn(Optional.of(ana));

        assertThat(userDetailsService.loadUserByUsername("ana").isEnabled()).isFalse();
    }

    @Test
    @DisplayName("marks the account as locked while the lock has not expired")
    void loadUserByUsername_lockedUser_shouldBeLocked() {
        var ana = user(10L, "ana");
        ana.registerFailedLogin(1, Duration.ofMinutes(15), NOW.minusSeconds(60));
        given(userRepository.findByUsername("ana")).willReturn(Optional.of(ana));

        assertThat(userDetailsService.loadUserByUsername("ana").isAccountNonLocked()).isFalse();
    }

    @Test
    @DisplayName("does not reveal whether the user exists: same generic message")
    void loadUserByUsername_missingUser_shouldThrowGenericMessage() {
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("nobody"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Invalid credentials");
    }
}
