package com.showcase.auth.infrastructure.scheduling;

import com.showcase.auth.application.service.AccountMaintenanceService;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Adaptador de entrada de las tareas programadas de auth-service, como un controller: solo llama al servicio.
 * Con varias réplicas, ShedLock garantiza que cada tarea la ejecuta una sola a la vez.
 */
@Component
@RequiredArgsConstructor
public class AccountMaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(AccountMaintenanceScheduler.class);

    private final AccountMaintenanceService maintenanceService;

    @Scheduled(fixedDelayString = "${showcase.scheduling.account-unlock.fixed-delay:1m}",
            initialDelayString = "${showcase.scheduling.account-unlock.initial-delay:30s}")
    @SchedulerLock(name = "accountUnlock", lockAtMostFor = "PT5M", lockAtLeastFor = "PT10S")
    public void unlockExpiredAccounts() {
        int unlocked = maintenanceService.unlockExpiredAccounts();
        if (unlocked > 0) {
            log.info("Desbloqueo automático: {} cuentas desbloqueadas", unlocked);
        }
    }

    @Scheduled(cron = "${showcase.scheduling.authorization-cleanup.cron:0 0 4 * * *}")
    @SchedulerLock(name = "expiredAuthorizationCleanup", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void deleteExpiredAuthorizations() {
        log.info("Limpieza de tokens caducados: {} autorizaciones OAuth2 borradas",
                maintenanceService.deleteExpiredAuthorizations());
    }
}
