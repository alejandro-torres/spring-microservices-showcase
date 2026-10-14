package com.showcase.product.infrastructure.lock.impl;

import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.DomainException;
import com.showcase.product.application.lock.DistributedLockManager;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.redisson.RedissonMultiLock;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

/**
 * Locks en Redis con Redisson, compartidos por todas las réplicas.
 * <ul>
 *   <li>Varias claves → {@link RedissonMultiLock}: se cogen todas o ninguna, siempre en orden alfabético.</li>
 *   <li>{@code tryLock} con espera y con expiración: nunca se espera indefinidamente, y si la réplica muere con el
 *       lock cogido, Redis lo suelta al expirar.</li>
 *   <li>Se suelta en un {@code finally}, solo si lo tiene este hilo.</li>
 * </ul>
 */
public class RedissonDistributedLockManagerImpl implements DistributedLockManager {

    private final RedissonClient redisson;
    private final Duration waitTime;
    private final Duration leaseTime;

    public RedissonDistributedLockManagerImpl(RedissonClient redisson, Duration waitTime, Duration leaseTime) {
        this.redisson = redisson;
        this.waitTime = waitTime;
        this.leaseTime = leaseTime;
    }

    @Override
    public <T> T executeWithLocks(Collection<String> keys, Supplier<T> action) {
        RLock[] locks = keys.stream().distinct().sorted().map(redisson::getLock).toArray(RLock[]::new);
        if (locks.length == 0) {
            return action.get();
        }
        RLock lock = locks.length == 1 ? locks[0] : new RedissonMultiLock(locks);
        boolean acquired;
        try {
            acquired = lock.tryLock(waitTime.toMillis(), leaseTime.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResourceBusyException(String.join(",", keys), e);
        }
        if (!acquired) {
            throw new ResourceBusyException(String.join(",", keys), null);
        }
        try {
            return action.get();
        } finally {
            unlock(lock, locks);
        }
    }

    private static void unlock(RLock lock, RLock[] locks) {
        if (lock instanceof RedissonMultiLock) {
            lock.unlock();
        } else if (locks[0].isHeldByCurrentThread()) {
            locks[0].unlock();
        }
    }

    /** RESOURCE_BUSY: 423 en la API y reintento si llega por un mensaje (ver MessagingExceptionClassifier). */
    static class ResourceBusyException extends DomainException {

        ResourceBusyException(String resource, Throwable cause) {
            super(CommonErrorCode.RESOURCE_BUSY, cause, resource);
        }
    }
}
