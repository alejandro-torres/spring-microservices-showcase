package com.showcase.auth.application.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.showcase.auth.application.security.RequestMetadata;
import com.showcase.auth.application.security.RequestMetadataProvider;
import com.showcase.auth.domain.model.SecurityAuditEvent;
import com.showcase.auth.domain.model.SecurityEventType;
import com.showcase.auth.domain.repository.SecurityAuditRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SecurityAuditServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-14T12:00:00Z");

    @Mock
    private SecurityAuditRepository auditRepository;

    @Mock
    private RequestMetadataProvider requestMetadataProvider;

    private SecurityAuditServiceImpl auditService;

    @BeforeEach
    void setUp() {
        auditService = new SecurityAuditServiceImpl(auditRepository, requestMetadataProvider,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private SecurityAuditEvent recorded() {
        var captor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
        verify(auditRepository).save(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("record")
    class Record {

        @BeforeEach
        void givenSaveReturnsArgument() {
            given(auditRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        }

        @Test
        @DisplayName("guarda el evento con la IP, el user-agent, el traceId y la fecha del Clock")
        void record_shouldSaveEventWithRequestMetadata() {
            given(requestMetadataProvider.current())
                    .willReturn(new RequestMetadata("10.0.0.7", "Mozilla/5.0", "trace-1", null));

            auditService.record(SecurityEventType.LOGIN_FAILURE, "ana", null);

            var event = recorded();
            assertThat(event.type()).isEqualTo(SecurityEventType.LOGIN_FAILURE);
            assertThat(event.username()).isEqualTo("ana");
            assertThat(event.ip()).isEqualTo("10.0.0.7");
            assertThat(event.userAgent()).isEqualTo("Mozilla/5.0");
            assertThat(event.traceId()).isEqualTo("trace-1");
            assertThat(event.occurredAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("anota quién hizo la operación cuando es otro usuario (p. ej. un ADMIN)")
        void record_byAnotherUser_shouldAddActor() {
            given(requestMetadataProvider.current()).willReturn(new RequestMetadata(null, null, null, "admin"));

            auditService.record(SecurityEventType.ROLE_ASSIGNED, "ana", "role=ADMIN");

            assertThat(recorded().details()).isEqualTo("role=ADMIN; by=admin");
        }

        @Test
        @DisplayName("no repite el actor cuando el usuario actúa sobre sí mismo")
        void record_bySameUser_shouldNotAddActor() {
            given(requestMetadataProvider.current()).willReturn(new RequestMetadata(null, null, null, "ana"));

            auditService.record(SecurityEventType.PASSWORD_CHANGED, "ana", null);

            assertThat(recorded().details()).isNull();
        }
    }

    @Nested
    @DisplayName("SecurityAuditEvent")
    class Event {

        @Test
        @DisplayName("recorta los datos externos demasiado largos y usa 'desconocido' si no hay usuario")
        void event_shouldTruncateAndDefaultUsername() {
            var event = SecurityAuditEvent.of(SecurityEventType.LOGIN_FAILURE, " ", null, "x".repeat(400),
                    null, "y".repeat(600), NOW);

            assertThat(event.username()).isEqualTo("desconocido");
            assertThat(event.userAgent()).hasSize(255);
            assertThat(event.details()).hasSize(500);
        }
    }
}
