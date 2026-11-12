package com.showcase.auth.domain.repository;

import java.time.Instant;

/**
 * OAuth2 authorizations stored by the authorization server (codes, access and refresh tokens issued).
 * Outbound port for their cleanup only: Spring Authorization Server manages everything else.
 */
public interface AuthorizationRepository {

    /**
     * Deletes the authorizations in which everything they hold has expired (code, access token, refresh
     * token...): they are no longer of any use.
     *
     * @return how many were deleted
     */
    int deleteFullyExpiredBefore(Instant now);
}
