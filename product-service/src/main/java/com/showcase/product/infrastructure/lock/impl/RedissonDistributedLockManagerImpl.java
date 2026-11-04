package com.showcase.product.infrastructure.lock.impl;

import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.DomainException;
import com.showcase.product.application.lock.DistributedLockManager;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.redisson.RedissonMultiLock;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

/**
 * Locks in Redis with Redisson, shared by all replicas.
 * <ul>
 *   <li>Several keys → {@link RedissonMultiLock}: all or none are taken, always in alphabetical order.</li>
 *   <li>{@code tryLock} with wait and expiry: it never waits indefinitely, and if the replica dies holding the
 *       lock, Redis releases it on expiry.</li>
 *   <li>It is released in a {@code finally}, only if this thread holds it.</li>
 * </ul>
 */
@RequiredArgsConstructor
public class RedissonDistributedLockManagerImpl implements DistributedLockManager {

    static final String WAIT_METRIC = "distributed.lock.wait";

    private final RedissonClient redisson;
    private final Duration waitTime;
    private final Duration leaseTime;
    /**
     * {@code distributed.lock.wait} metric: how long is spent waiting for locks, separating those acquired
     * ({@code outcome=acquired}) from those given up on ({@code outcome=busy}).
     */
    private final MeterRegistry meterRegistry;

    /** Micrometer returns the same timer if already registered with that name and tags. */
    private Timer waitTimer(String outcome) {
        return Timer.builder(WAIT_METRIC)
                .description("Wait time for distributed locks")
                .tag("outcome", outcome)
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    @Override
    public <T> T executeWithLocks(Collection<String> keys, Supplier<T> action) {
        RLock[] locks = keys.stream().distinct().sorted().map(redisson::getLock).toArray(RLock[]::new);
        if (locks.length == 0) {
            return action.get();
        }
        RLock lock = locks.length == 1 ? locks[0] : new RedissonMultiLock(locks);
        boolean acquired;
        long start = System.nanoTime();
        try {
            acquired = lock.tryLock(waitTime.toMillis(), leaseTime.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            waitTimer("busy").record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            throw new ResourceBusyException(String.join(",", keys), e);
        }
        waitTimer(acquired ? "acquired" : "busy").record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
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

    /** RESOURCE_BUSY: 423 in the API and a retry if it comes from a message (see MessagingExceptionClassifier). */
    static class ResourceBusyException extends DomainException {

        ResourceBusyException(String resource, Throwable cause) {
            super(CommonErrorCode.RESOURCE_BUSY, cause, resource);
        }
    }
}
