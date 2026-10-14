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
import org.springframework.test.web.servlet.MockMvc;

/**
 * Bloqueo de cuentas con transacciones reales: demuestra {@code noRollbackFor} y el flujo completo de login.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountLockoutIT {

    private static final String PASSWORD = "Secreta-Test-123";

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
    @DisplayName("noRollbackFor: el bloqueo se guarda aunque onFailure termine lanzando AccountLockedException")
    void noRollbackFor_shouldPersistLockDespiteException() {
        userService.create("lock.service", "lock.service@example.com", PASSWORD, Set.of());

        for (int i = 0; i < 4; i++) {
            loginAttemptService.onFailure("lock.service");
        }
        assertThat(userJpaRepository.findByUsernameAndActiveTrue("lock.service").orElseThrow().getFailedAttempts())
                .isEqualTo(4);

        assertThatThrownBy(() -> loginAttemptService.onFailure("lock.service"))
                .isInstanceOf(AccountLockedException.class);

        // La excepción salió del método @Transactional y, aun así, el bloqueo está en la base de datos.
        var stored = userJpaRepository.findByUsernameAndActiveTrue("lock.service").orElseThrow();
        assertThat(stored.getLockedUntil()).isNotNull();
        assertThat(stored.getFailedAttempts()).isZero();
    }

    @Test
    @DisplayName("tras 5 contraseñas incorrectas la cuenta se bloquea, y un ADMIN puede desbloquearla")
    void fiveFailedLogins_shouldLockUntilAdminUnlocks() throws Exception {
        long userId = userService.create("lock.form", "lock.form@example.com", PASSWORD, Set.of()).getId();

        for (int i = 0; i < 5; i++) {
            login("lock.form", "incorrecta", "/login?error");
        }
        // Con la cuenta bloqueada, ni siquiera la contraseña correcta permite entrar.
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
