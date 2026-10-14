package com.showcase.auth.application.metrics;

/**
 * Métricas de negocio y de seguridad de auth-service (puerto de salida); la implementación con Micrometer está en
 * la infraestructura.
 */
public interface AuthMetrics {

    void userRegistered();

    void loginSucceeded();

    void loginFailed();

    void accountLocked();
}
