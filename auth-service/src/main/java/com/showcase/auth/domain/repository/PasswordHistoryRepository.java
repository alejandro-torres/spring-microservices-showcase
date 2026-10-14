package com.showcase.auth.domain.repository;

import java.time.Instant;
import java.util.List;

/**
 * Puerto de salida del historial de contraseñas (solo hashes, nunca contraseñas en claro).
 */
public interface PasswordHistoryRepository {

    void save(Long userId, String passwordHash, Instant createdAt);

    /** Los últimos {@code limit} hashes del usuario, del más reciente al más antiguo. */
    List<String> findRecentHashes(Long userId, int limit);
}
