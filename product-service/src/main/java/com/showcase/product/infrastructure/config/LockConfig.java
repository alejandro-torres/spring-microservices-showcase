package com.showcase.product.infrastructure.config;

import com.showcase.product.application.lock.DistributedLockManager;
import com.showcase.product.infrastructure.lock.impl.RedissonDistributedLockManagerImpl;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson client using the same Redis as the gateway and auth-service ({@code spring.data.redis.*}).
 * The lock timings are configured in {@code showcase.lock}.
 */
@Configuration(proxyBeanMethods = false)
public class LockConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(@Value("${spring.data.redis.host}") String host,
                                         @Value("${spring.data.redis.port}") int port) {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + host + ":" + port);
        return Redisson.create(config);
    }

    /**
     * @param waitTime  how long to wait for another replica to release the lock before giving up
     * @param leaseTime lock expiry in case the replica holding it dies without releasing it
     */
    @Bean
    public DistributedLockManager distributedLockManager(RedissonClient redisson, MeterRegistry meterRegistry,
                                                         @Value("${showcase.lock.wait-time:3s}") Duration waitTime,
                                                         @Value("${showcase.lock.lease-time:10s}") Duration leaseTime) {
        return new RedissonDistributedLockManagerImpl(redisson, waitTime, leaseTime, meterRegistry);
    }
}
