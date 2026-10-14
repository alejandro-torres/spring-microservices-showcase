package com.showcase.product.infrastructure.config;

import com.showcase.product.application.lock.DistributedLockManager;
import com.showcase.product.infrastructure.lock.impl.RedissonDistributedLockManagerImpl;
import java.time.Duration;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cliente de Redisson con el mismo Redis que usan el gateway y auth-service ({@code spring.data.redis.*}).
 * Los tiempos del lock se configuran en {@code showcase.lock}.
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
     * @param waitTime  cuánto se espera a que otra réplica suelte el lock antes de rendirse
     * @param leaseTime caducidad del lock por si la réplica que lo tiene muere sin soltarlo
     */
    @Bean
    public DistributedLockManager distributedLockManager(RedissonClient redisson,
                                                         @Value("${showcase.lock.wait-time:3s}") Duration waitTime,
                                                         @Value("${showcase.lock.lease-time:10s}") Duration leaseTime) {
        return new RedissonDistributedLockManagerImpl(redisson, waitTime, leaseTime);
    }
}
