package com.showcase.auth.application.service;

/**
 * Seguimiento de los intentos de login para bloquear cuentas atacadas por fuerza bruta.
 */
public interface LoginAttemptService {

    /** Login correcto: reinicia el contador y lo audita. */
    void onSuccess(String username);

    /**
     * Login fallido por contraseña incorrecta: incrementa el contador y, al llegar al máximo, bloquea la cuenta.
     *
     * @throws com.showcase.auth.domain.exception.AccountLockedException si este intento bloquea la cuenta
     */
    void onFailure(String username);
}
