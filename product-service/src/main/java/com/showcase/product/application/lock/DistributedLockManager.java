package com.showcase.product.application.lock;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * Locks compartidos por todas las réplicas del servicio (puerto de salida). Los {@code *ServiceImpl} solo conocen
 * esta interfaz; la implementación con Redisson está en la infraestructura.
 */
public interface DistributedLockManager {

    /**
     * Coge todos los locks (ordenados, para que dos peticiones nunca los pidan en orden distinto y se bloqueen
     * mutuamente), ejecuta la acción y los suelta siempre, también si la acción falla.
     *
     * @throws com.showcase.common.domain.exception.DomainException con {@code RESOURCE_BUSY} si no se consiguen
     *                                                               a tiempo
     */
    <T> T executeWithLocks(Collection<String> keys, Supplier<T> action);
}
