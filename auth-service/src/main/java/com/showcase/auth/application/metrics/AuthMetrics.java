package com.showcase.auth.application.metrics;

/**
 * Business and security metrics of auth-service (outbound port); the Micrometer implementation is in the
 * infrastructure.
 */
public interface AuthMetrics {

    void userRegistered();

    void loginSucceeded();

    void loginFailed();

    void accountLocked();
}
