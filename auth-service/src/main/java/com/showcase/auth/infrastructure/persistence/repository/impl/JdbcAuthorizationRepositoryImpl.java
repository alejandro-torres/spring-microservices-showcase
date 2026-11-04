package com.showcase.auth.infrastructure.persistence.repository.impl;

import com.showcase.auth.domain.repository.AuthorizationRepository;
import java.sql.Timestamp;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The {@code oauth2_authorization} table belongs to Spring Authorization Server (it has no JPA entity), so it is
 * cleaned with JDBC. {@code GREATEST} ignores PostgreSQL NULLs: it takes the latest expiry date of what each
 * row contains, and deletes only if it has passed.
 */
@Repository
@RequiredArgsConstructor
public class JdbcAuthorizationRepositoryImpl implements AuthorizationRepository {

    private static final String DELETE_EXPIRED = """
            DELETE FROM oauth2_authorization
            WHERE GREATEST(authorization_code_expires_at, access_token_expires_at, refresh_token_expires_at,
                           oidc_id_token_expires_at, user_code_expires_at, device_code_expires_at) < :now
            """;

    private final JdbcClient jdbcClient;

    @Override
    public int deleteFullyExpiredBefore(Instant now) {
        return jdbcClient.sql(DELETE_EXPIRED).param("now", Timestamp.from(now)).update();
    }
}
