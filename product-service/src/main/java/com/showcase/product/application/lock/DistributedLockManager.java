package com.showcase.product.application.lock;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * Locks shared by all replicas of the service (outbound port). The {@code *ServiceImpl} classes only know
 * this interface; the Redisson implementation is in the infrastructure.
 */
public interface DistributedLockManager {

    /**
     * Takes all the locks (sorted, so that two requests never request them in a different order and deadlock each
     * other), runs the action and always releases them, even if the action fails.
     *
     * @throws com.showcase.common.domain.exception.DomainException with {@code RESOURCE_BUSY} if they cannot be
     *                                                               acquired in time
     */
    <T> T executeWithLocks(Collection<String> keys, Supplier<T> action);
}
