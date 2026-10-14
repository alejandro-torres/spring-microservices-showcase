package com.showcase.auth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.showcase.auth.domain.model.User;
import com.showcase.auth.domain.repository.UserRepository;
import com.showcase.auth.infrastructure.persistence.repository.impl.JdbcAuthorizationRepositoryImpl;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Consultas de las tareas de mantenimiento contra PostgreSQL real. */
class MaintenanceRepositoriesIT extends AbstractPersistenceIT {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DataSource dataSource;

    private User saveUser(String username, Instant lockedAt) {
        User user = userRepository.save(User.create(username, username + "@example.com", "{bcrypt}x", Set.of()));
        if (lockedAt != null) {
            user.registerFailedLogin(1, Duration.ofMinutes(15), lockedAt);
            user = userRepository.save(user);
        }
        return user;
    }

    @Nested
    @DisplayName("findWithExpiredLock")
    class FindWithExpiredLock {

        @Test
        @DisplayName("devuelve solo las cuentas cuyo bloqueo ya ha caducado")
        void findWithExpiredLock_shouldReturnOnlyExpiredLocks() {
            // given
            User expired = saveUser("lock.expired", NOW.minus(Duration.ofHours(1)));
            saveUser("lock.active", NOW.minus(Duration.ofMinutes(5)));
            saveUser("lock.never", null);

            // when / then
            assertThat(userRepository.findWithExpiredLock(NOW, 100))
                    .extracting(User::getId)
                    .containsExactly(expired.getId());
        }
    }

    @Nested
    @DisplayName("deleteFullyExpiredBefore")
    class DeleteFullyExpiredBefore {

        @Test
        @DisplayName("borra solo las autorizaciones en las que ha caducado todo, también el refresh token")
        void deleteFullyExpiredBefore_shouldKeepAuthorizationsWithValidRefreshToken() {
            // given
            JdbcClient jdbc = JdbcClient.create(dataSource);
            insertAuthorization(jdbc, "all-expired", NOW.minusSeconds(3600), NOW.minusSeconds(60));
            insertAuthorization(jdbc, "refresh-valid", NOW.minusSeconds(3600), NOW.plusSeconds(3600));
            insertAuthorization(jdbc, "access-only-expired", NOW.minusSeconds(60), null);

            // when
            int deleted = new JdbcAuthorizationRepositoryImpl(jdbc).deleteFullyExpiredBefore(NOW);

            // then
            assertThat(deleted).isEqualTo(2);
            assertThat(jdbc.sql("SELECT id FROM oauth2_authorization").query(String.class).list())
                    .containsExactly("refresh-valid");
        }

        private void insertAuthorization(JdbcClient jdbc, String id, Instant accessExpiresAt,
                                         Instant refreshExpiresAt) {
            jdbc.sql("""
                    INSERT INTO oauth2_authorization (id, registered_client_id, principal_name,
                                                      authorization_grant_type, access_token_expires_at,
                                                      refresh_token_expires_at)
                    VALUES (:id, 'web-client', 'ana', 'authorization_code', :access, :refresh)""")
                    .param("id", id)
                    .param("access", Timestamp.from(accessExpiresAt))
                    .param("refresh", refreshExpiresAt == null ? null : Timestamp.from(refreshExpiresAt))
                    .update();
        }
    }
}
