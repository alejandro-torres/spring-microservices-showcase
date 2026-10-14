package com.showcase.auth.application.service.impl;

import static com.showcase.auth.testdata.AuthMother.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.showcase.auth.application.service.SecurityAuditService;
import com.showcase.auth.domain.model.SecurityEventType;
import com.showcase.auth.domain.repository.AuthorizationRepository;
import com.showcase.auth.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountMaintenanceServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-14T12:00:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthorizationRepository authorizationRepository;

    @Mock
    private SecurityAuditService auditService;

    private AccountMaintenanceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AccountMaintenanceServiceImpl(userRepository, authorizationRepository, auditService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Nested
    @DisplayName("unlockExpiredAccounts")
    class UnlockExpiredAccounts {

        @Test
        @DisplayName("desbloquea las cuentas con el bloqueo caducado y lo audita como automático")
        void unlockExpiredAccounts_shouldUnlockAndAudit() {
            // given: se bloqueó hace una hora durante 15 minutos
            var ana = user(10L, "ana");
            ana.registerFailedLogin(1, Duration.ofMinutes(15), NOW.minus(Duration.ofHours(1)));
            given(userRepository.findWithExpiredLock(NOW, AccountMaintenanceServiceImpl.BATCH_SIZE))
                    .willReturn(List.of(ana));

            // when
            int unlocked = service.unlockExpiredAccounts();

            // then
            assertThat(unlocked).isEqualTo(1);
            assertThat(ana.getLockedUntil()).isNull();
            assertThat(ana.getFailedAttempts()).isZero();
            verify(userRepository).save(ana);
            verify(auditService).record(SecurityEventType.ACCOUNT_UNLOCKED, "ana", "automatic");
        }

        @Test
        @DisplayName("sin cuentas pendientes no guarda ni audita nada")
        void unlockExpiredAccounts_withoutLockedAccounts_shouldDoNothing() {
            // given
            given(userRepository.findWithExpiredLock(NOW, AccountMaintenanceServiceImpl.BATCH_SIZE))
                    .willReturn(List.of());

            // when
            int unlocked = service.unlockExpiredAccounts();

            // then
            assertThat(unlocked).isZero();
            verify(userRepository, never()).save(any());
            verify(auditService, never()).record(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("deleteExpiredAuthorizations")
    class DeleteExpiredAuthorizations {

        @Test
        @DisplayName("borra las autorizaciones caducadas con la hora actual")
        void deleteExpiredAuthorizations_shouldDelegateWithNow() {
            // given
            given(authorizationRepository.deleteFullyExpiredBefore(NOW)).willReturn(3);

            // when / then
            assertThat(service.deleteExpiredAuthorizations()).isEqualTo(3);
        }
    }
}
