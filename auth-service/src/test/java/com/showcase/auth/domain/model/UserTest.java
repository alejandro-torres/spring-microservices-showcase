package com.showcase.auth.domain.model;

import static com.showcase.auth.testdata.AuthMother.PASSWORD_HASH;
import static com.showcase.auth.testdata.AuthMother.adminRole;
import static com.showcase.auth.testdata.AuthMother.user;
import static com.showcase.auth.testdata.AuthMother.userRole;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.showcase.auth.domain.exception.AuthErrorCode;
import com.showcase.common.domain.exception.BusinessRuleException;
import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.ConflictException;
import com.showcase.common.domain.exception.InvalidRequestException;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UserTest {

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("creates an active, enabled user, normalizing username and email")
        void create_shouldNormalizeAndActivate() {
            // when
            var user = User.create("  Ana.Garcia ", " Ana@Example.COM ", PASSWORD_HASH, Set.of(userRole()));

            // then
            assertThat(user.getId()).isNull();
            assertThat(user.getUsername()).isEqualTo("ana.garcia");
            assertThat(user.getEmail()).isEqualTo("ana@example.com");
            assertThat(user.isEnabled()).isTrue();
            assertThat(user.isActive()).isTrue();
            assertThat(user.getRoles()).extracting(Role::getName).containsExactly("USER");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"ab", "with spaces", "too-long-to-be-a-valid-username", "ñandu"})
        @DisplayName("rejects a username with an invalid format with INVALID_USERNAME")
        void create_withInvalidUsername_shouldThrow(String username) {
            assertThatThrownBy(() -> User.create(username, "ana@example.com", PASSWORD_HASH, Set.of()))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_USERNAME);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"no-at-sign", "a@b", "dos@@example.com"})
        @DisplayName("rejects an email with an invalid format with INVALID_EMAIL")
        void create_withInvalidEmail_shouldThrow(String email) {
            assertThatThrownBy(() -> User.create("ana", email, PASSWORD_HASH, Set.of()))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_EMAIL);
        }

        @Test
        @DisplayName("requires the password already hashed")
        void create_withoutPasswordHash_shouldFail() {
            assertThatThrownBy(() -> User.create("ana", "ana@example.com", null, Set.of()))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("roles")
    class Roles {

        @Test
        @DisplayName("assigns a new role")
        void assignRole_shouldAddRole() {
            var user = user(10L, "ana", userRole());
            user.assignRole(adminRole());
            assertThat(user.getRoles()).extracting(Role::getName).containsExactlyInAnyOrder("USER", "ADMIN");
        }

        @Test
        @DisplayName("rejects assigning a role the user already has with ROLE_ALREADY_ASSIGNED")
        void assignRole_alreadyAssigned_shouldThrow() {
            var user = user(10L, "ana", userRole());
            assertThatThrownBy(() -> user.assignRole(userRole()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.ROLE_ALREADY_ASSIGNED);
        }

        @Test
        @DisplayName("removes an assigned role and rejects removing one the user does not have")
        void removeRole_shouldRemoveOrThrow() {
            var user = user(10L, "ana", userRole(), adminRole());

            user.removeRole(adminRole().getId());

            assertThat(user.hasRole(adminRole().getId())).isFalse();
            assertThatThrownBy(() -> user.removeRole(99L))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.ROLE_NOT_ASSIGNED);
        }

        @Test
        @DisplayName("does not allow modifying the roles from outside the model")
        void getRoles_shouldBeUnmodifiable() {
            var user = user(10L, "ana", userRole());
            assertThatThrownBy(() -> user.getRoles().clear()).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("lockout after failed attempts")
    class Lockout {

        private static final java.time.Instant NOW = java.time.Instant.parse("2026-10-14T12:00:00Z");
        private static final java.time.Duration LOCK = java.time.Duration.ofMinutes(15);

        @Test
        @DisplayName("counts failures and locks on reaching the maximum, resetting the counter")
        void registerFailedLogin_shouldLockAtMaxAttempts() {
            var user = user(10L, "ana");

            for (int i = 1; i <= 4; i++) {
                assertThat(user.registerFailedLogin(5, LOCK, NOW)).isFalse();
                assertThat(user.getFailedAttempts()).isEqualTo(i);
            }
            assertThat(user.registerFailedLogin(5, LOCK, NOW)).isTrue();

            assertThat(user.isLocked(NOW)).isTrue();
            assertThat(user.getLockedUntil()).isEqualTo(NOW.plus(LOCK));
            assertThat(user.getFailedAttempts()).isZero();
        }

        @Test
        @DisplayName("the lock expires on its own and attempts are not counted while it lasts")
        void lock_shouldExpireAndIgnoreAttemptsWhileLocked() {
            var user = user(10L, "ana");
            user.registerFailedLogin(1, LOCK, NOW);

            assertThat(user.registerFailedLogin(1, LOCK, NOW.plusSeconds(60))).isFalse();
            assertThat(user.getFailedAttempts()).isZero();
            assertThat(user.isLocked(NOW.plus(LOCK))).isFalse();
        }

        @Test
        @DisplayName("a successful login resets the counter and unlock removes the lock")
        void resetAndUnlock_shouldClearState() {
            var user = user(10L, "ana");
            user.registerFailedLogin(5, LOCK, NOW);
            user.resetFailedLogins();
            assertThat(user.getFailedAttempts()).isZero();

            user.registerFailedLogin(1, LOCK, NOW);
            user.unlock();
            assertThat(user.isLocked(NOW)).isFalse();
            assertThat(user.getLockedUntil()).isNull();
        }
    }

    @Nested
    @DisplayName("update, deactivate and checkVersion")
    class Lifecycle {

        @Test
        @DisplayName("updates the normalized email and the enabled status")
        void update_shouldChangeEmailAndEnabled() {
            var user = user(10L, "ana");
            user.update("NEW@example.com", false);
            assertThat(user.getEmail()).isEqualTo("new@example.com");
            assertThat(user.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("soft delete also disables the user")
        void deactivate_shouldAlsoDisable() {
            var user = user(10L, "ana");
            user.deactivate();
            assertThat(user.isActive()).isFalse();
            assertThat(user.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("accepts the current version and rejects another with CONCURRENT_MODIFICATION")
        void checkVersion_shouldDetectConcurrentChanges() {
            var user = user(10L, "ana");
            user.checkVersion(0L);
            assertThatThrownBy(() -> user.checkVersion(1L))
                    .isInstanceOf(ConflictException.class)
                    .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.CONCURRENT_MODIFICATION);
        }
    }
}
