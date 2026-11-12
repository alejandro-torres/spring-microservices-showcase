package com.showcase.auth.domain.repository;

import java.time.Instant;
import java.util.List;

/**
 * Outbound port of the password history (hashes only, never plain-text passwords).
 */
public interface PasswordHistoryRepository {

    void save(Long userId, String passwordHash, Instant createdAt);

    /** The user's last {@code limit} hashes, from most recent to oldest. */
    List<String> findRecentHashes(Long userId, int limit);
}
