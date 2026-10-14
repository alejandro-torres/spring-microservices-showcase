package com.showcase.auth.application.service.impl;

import static com.showcase.auth.testdata.AuthMother.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.showcase.auth.application.security.LoginPolicy;
import com.showcase.auth.application.service.SecurityAuditService;
import com.showcase.auth.domain.event.AccountLockedEvent;
import com.showcase.auth.domain.exception.AccountLockedException;
import com.showcase.auth.domain.messaging.UserEventPublisher;
import com.showcase.auth.domain.exception.AuthErrorCode;
import com.showcase.auth.domain.model.SecurityEventType;
import com.showcase.auth.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LoginAttemptServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-14T12:00:00Z");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final SecurityAuditService auditService = mock(SecurityAuditService.class);
    private final UserEventPublisher eventPublisher = mock(UserEventPublisher.class);
    private final LoginAttemptServiceImpl service = new LoginAttemptServiceImpl(userRepository, auditService,
            new LoginPolicy(3, Duration.ofMinutes(15)), eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));

    @Nested
    @DisplayName("onFailure")
    class OnFailure {

        @Test
        @DisplayName("incrementa el contador, lo guarda y audita el fallo")
        void onFailure_shouldIncrementAndAudit() {
            var ana = user(10L, "ana");
            given(userRepository.findByUsername("ana")).willReturn(Optional.of(ana));

            service.onFailure(" ANA ");

            assertThat(ana.getFailedAttempts()).isEqualTo(1);
            verify(userRepository).save(ana);
            verify(auditService).record(SecurityEventType.LOGIN_FAILURE, "ana", "attempts=1");
        }

        @Test
        @DisplayName("al llegar al máximo guarda el bloqueo ANTES de lanzar AccountLockedException")
        void onFailure_atMaxAttempts_shouldSaveLockThenThrow() {
            var ana = user(10L, "ana");
            given(userRepository.findByUsername("ana")).willReturn(Optional.of(ana));
            service.onFailure("ana");
            service.onFailure("ana");

            assertThatThrownBy(() -> service.onFailure("ana"))
                    .isInstanceOf(AccountLockedException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.ACCOUNT_LOCKED);

            assertThat(ana.getLockedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
            var order = inOrder(userRepository, auditService);
            order.verify(userRepository).save(ana);
            order.verify(auditService).record(eq(SecurityEventType.ACCOUNT_LOCKED), eq("ana"), anyString());
            verify(eventPublisher).publish(new AccountLockedEvent(10L, "ana", NOW.plus(Duration.ofMinutes(15)), NOW));
        }

        @Test
        @DisplayName("audita los intentos con usuarios inexistentes sin guardar nada")
        void onFailure_unknownUser_shouldOnlyAudit() {
            service.onFailure("no.existe");

            verify(auditService).record(SecurityEventType.LOGIN_FAILURE, "no.existe", "unknown user");
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("onSuccess")
    class OnSuccess {

        @Test
        @DisplayName("reinicia el contador si había fallos y audita el login")
        void onSuccess_withPreviousFailures_shouldReset() {
            var ana = user(10L, "ana");
            ana.registerFailedLogin(3, Duration.ofMinutes(15), NOW);
            given(userRepository.findByUsername("ana")).willReturn(Optional.of(ana));

            service.onSuccess("ana");

            assertThat(ana.getFailedAttempts()).isZero();
            verify(userRepository).save(ana);
            verify(auditService).record(SecurityEventType.LOGIN_SUCCESS, "ana", null);
        }

        @Test
        @DisplayName("no guarda nada si no había fallos")
        void onSuccess_withoutFailures_shouldNotSave() {
            given(userRepository.findByUsername("ana")).willReturn(Optional.of(user(10L, "ana")));

            service.onSuccess("ana");

            verify(userRepository, never()).save(any());
        }
    }
}
