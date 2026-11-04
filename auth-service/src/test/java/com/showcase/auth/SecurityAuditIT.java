package com.showcase.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.showcase.auth.application.service.SecurityAuditService;
import com.showcase.auth.domain.model.SecurityAuditEvent;
import com.showcase.auth.domain.model.SecurityEventType;
import com.showcase.auth.infrastructure.persistence.repository.UserJpaRepository;
import com.showcase.auth.infrastructure.persistence.entity.UserEntity;
import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.filter.FilterOperator;
import com.showcase.common.domain.page.PageQuery;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Demonstrates the behavior of {@code Propagation.REQUIRES_NEW} with real transactions in PostgreSQL.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SecurityAuditIT {

    @Autowired
    private SecurityAuditService auditService;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private List<SecurityAuditEvent> eventsFor(String username) {
        return auditService.findAll(List.of(FilterCriterion.of("username", FilterOperator.EQ, username)),
                PageQuery.firstPage()).content();
    }

    @Test
    @DisplayName("REQUIRES_NEW: the audit record is saved even if the transaction causing it rolls back")
    void audit_shouldSurviveRollbackOfCallerTransaction() {
        // when: within a transaction a value is changed, audited and then it fails
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            UserEntity entity = new UserEntity();
            entity.setUsername("rollback.user");
            entity.setEmail("rollback@example.com");
            entity.setPasswordHash("{bcrypt}x");
            userJpaRepository.saveAndFlush(entity);

            auditService.record(SecurityEventType.LOGIN_FAILURE, "rollback.user", "test attempt");

            throw new IllegalStateException("failure after auditing");
        })).hasMessage("failure after auditing");

        // then: the main transaction's change has been undone, but the audit event is still there
        assertThat(userJpaRepository.existsByUsername("rollback.user")).isFalse();
        assertThat(eventsFor("rollback.user")).singleElement()
                .satisfies(event -> {
                    assertThat(event.type()).isEqualTo(SecurityEventType.LOGIN_FAILURE);
                    assertThat(event.details()).isEqualTo("test attempt");
                });
    }

    @Test
    @DisplayName("filters the audit log by event type and sorts it by date")
    void findAll_shouldFilterByEventType() {
        auditService.record(SecurityEventType.LOGIN_SUCCESS, "filtro.user", null);
        auditService.record(SecurityEventType.ACCOUNT_LOCKED, "filtro.user", null);

        var locked = auditService.findAll(List.of(
                FilterCriterion.of("username", FilterOperator.EQ, "filtro.user"),
                FilterCriterion.of("eventType", FilterOperator.EQ, SecurityEventType.ACCOUNT_LOCKED)),
                PageQuery.firstPage());

        assertThat(locked.content()).extracting(SecurityAuditEvent::type).containsExactly(SecurityEventType.ACCOUNT_LOCKED);
        assertThat(eventsFor("filtro.user")).hasSize(2);
    }
}
