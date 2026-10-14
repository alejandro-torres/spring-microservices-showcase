package com.showcase.auth.application.service;

/**
 * Mantenimiento periódico de las cuentas y los tokens (puerto de entrada de las tareas programadas).
 */
public interface AccountMaintenanceService {

    /**
     * Desbloquea las cuentas cuyo bloqueo por intentos fallidos ya ha caducado y deja constancia en la auditoría.
     *
     * @return cuántas se han desbloqueado
     */
    int unlockExpiredAccounts();

    /**
     * Borra las autorizaciones OAuth2 en las que ya ha caducado todo (códigos, access y refresh tokens).
     *
     * @return cuántas se han borrado
     */
    int deleteExpiredAuthorizations();
}
