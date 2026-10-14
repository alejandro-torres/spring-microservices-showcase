package com.showcase.auth.infrastructure.persistence.repository.impl;

import com.showcase.auth.domain.repository.AuthorizationRepository;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * La tabla {@code oauth2_authorization} es de Spring Authorization Server (no tiene entidad JPA), así que se
 * limpia con JDBC. {@code GREATEST} ignora los NULL de PostgreSQL: se toma la última fecha de caducidad de lo que
 * contenga cada fila, y solo se borra si ya ha pasado.
 */
@Repository
public class JdbcAuthorizationRepositoryImpl implements AuthorizationRepository {

    private static final String DELETE_EXPIRED = """
            DELETE FROM oauth2_authorization
            WHERE GREATEST(authorization_code_expires_at, access_token_expires_at, refresh_token_expires_at,
                           oidc_id_token_expires_at, user_code_expires_at, device_code_expires_at) < :now
            """;

    private final JdbcClient jdbcClient;

    public JdbcAuthorizationRepositoryImpl(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public int deleteFullyExpiredBefore(Instant now) {
        return jdbcClient.sql(DELETE_EXPIRED).param("now", Timestamp.from(now)).update();
    }
}
