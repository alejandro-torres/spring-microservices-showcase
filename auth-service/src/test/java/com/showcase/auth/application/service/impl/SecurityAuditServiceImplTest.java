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
        @DisplayName("saves the event with the IP, the user-agent, the traceId and the Clock's date")
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
        @DisplayName("records who performed the operation when it is another user (e.g. an ADMIN)")
        void record_byAnotherUser_shouldAddActor() {
            given(requestMetadataProvider.current()).willReturn(new RequestMetadata(null, null, null, "admin"));

            auditService.record(SecurityEventType.ROLE_ASSIGNED, "ana", "role=ADMIN");

            assertThat(recorded().details()).isEqualTo("role=ADMIN; by=admin");
        }

        @Test
        @DisplayName("does not repeat the actor when the user acts on themselves")
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
        @DisplayName("trims external data that is too long and uses 'unknown' if there is no user")
        void event_shouldTruncateAndDefaultUsername() {
            var event = SecurityAuditEvent.of(SecurityEventType.LOGIN_FAILURE, " ", null, "x".repeat(400),
                    null, "y".repeat(600), NOW);

            assertThat(event.username()).isEqualTo("unknown");
            assertThat(event.userAgent()).hasSize(255);
            assertThat(event.details()).hasSize(500);
        }
    }
}
