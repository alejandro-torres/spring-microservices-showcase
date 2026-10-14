package com.showcase.auth.domain.repository;

import java.time.Instant;

/**
 * Autorizaciones OAuth2 guardadas por el servidor de autorización (códigos, access y refresh tokens emitidos).
 * Puerto de salida solo para su limpieza: el resto lo gestiona Spring Authorization Server.
 */
public interface AuthorizationRepository {

    /**
     * Borra las autorizaciones en las que ya ha caducado todo lo que contienen (código, access token, refresh
     * token...): ya no sirven para nada.
     *
     * @return cuántas se han borrado
     */
    int deleteFullyExpiredBefore(Instant now);
}
