package com.showcase.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.showcase.auth.application.service.LoginAttemptService;
import com.showcase.auth.application.service.SecurityAuditService;
import com.showcase.auth.application.service.UserService;
import com.showcase.auth.domain.exception.AccountLockedException;
import com.showcase.auth.domain.model.SecurityAuditEvent;
import com.showcase.auth.domain.model.SecurityEventType;
import com.showcase.auth.infrastructure.persistence.repository.UserJpaRepository;
import com.showcase.auth.testdata.TestJwt;
import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.filter.FilterOperator;
import com.showcase.common.domain.page.PageQuery;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Account locking with real transactions: demonstrates {@code noRollbackFor} and the full login flow.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountLockoutIT {

    private static final String PASSWORD = "Secret-Test-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private SecurityAuditService auditService;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private JdbcClient jdbcClient;

    private void login(String username, String password, String expectedRedirect) throws Exception {
        mockMvc.perform(post("/login").with(csrf()).param("username", username).param("password", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).contains(expectedRedirect));
    }

    private List<SecurityEventType> auditedEvents(String username) {
        return auditService.findAll(List.of(FilterCriterion.of("username", FilterOperator.EQ, username)),
                        new PageQuery(0, 50, "id", com.showcase.common.domain.page.SortDirection.ASC))
                .content().stream().map(SecurityAuditEvent::type).toList();
    }

    @Test
    @DisplayName("noRollbackFor: the lock is saved even though onFailure ends up throwing AccountLockedException")
    void noRollbackFor_shouldPersistLockDespiteException() {
        userService.create("lock.service", "lock.service@example.com", PASSWORD, Set.of());

        for (int i = 0; i < 4; i++) {
            loginAttemptService.onFailure("lock.service");
        }
        assertThat(userJpaRepository.findByUsernameAndActiveTrue("lock.service").orElseThrow().getFailedAttempts())
                .isEqualTo(4);

        assertThatThrownBy(() -> loginAttemptService.onFailure("lock.service"))
                .isInstanceOf(AccountLockedException.class);

        // The exception left the @Transactional method and, even so, the lock is in the database.
        var stored = userJpaRepository.findByUsernameAndActiveTrue("lock.service").orElseThrow();
        assertThat(stored.getLockedUntil()).isNotNull();
        assertThat(stored.getFailedAttempts()).isZero();
        // And the outbox's account.locked event committed in the same transaction.
        assertThat(jdbcClient.sql("SELECT count(*) FROM outbox_event WHERE event_type = 'account.locked' "
                        + "AND aggregate_id = :id")
                .param("id", String.valueOf(stored.getId())).query(Long.class).single()).isEqualTo(1);
    }

    @Test
    @DisplayName("after 5 wrong passwords the account locks, and an ADMIN can unlock it")
    void fiveFailedLogins_shouldLockUntilAdminUnlocks() throws Exception {
        long userId = userService.create("lock.form", "lock.form@example.com", PASSWORD, Set.of()).getId();

        for (int i = 0; i < 5; i++) {
            login("lock.form", "wrong", "/login?error");
        }
        // With the account locked, not even the correct password allows login.
        login("lock.form", PASSWORD, "/login?error");

        assertThat(auditedEvents("lock.form"))
                .containsSubsequence(SecurityEventType.LOGIN_FAILURE, SecurityEventType.ACCOUNT_LOCKED)
                .endsWith(SecurityEventType.LOGIN_FAILURE);

        mockMvc.perform(put("/api/v1/users/{id}/unlock", userId).with(TestJwt.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockedUntil").doesNotExist());

        login("lock.form", PASSWORD, "/");
        assertThat(auditedEvents("lock.form"))
                .endsWith(SecurityEventType.ACCOUNT_UNLOCKED, SecurityEventType.LOGIN_SUCCESS);
    }
}
