package com.showcase.gateway.infrastructure.ratelimit;

import java.net.InetSocketAddress;
import java.security.Principal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Who requests are counted against in rate limiting (token bucket in Redis, shared by all gateway replicas):
 * <ul>
 *   <li>Authenticated user: the JWT {@code sub}, so a user cannot dodge the limit by changing IP.</li>
 *   <li>Public routes (login, registration): the client IP.</li>
 * </ul>
 * The IP is that of the connection. {@code X-Forwarded-For} is only considered if there are trusted proxies in front
 * of the gateway ({@code showcase.rate-limit.trusted-proxy-hops}), and only the entry added by the last of them:
 * if the header were read as is, a client could invent a different IP on each attempt and bypass the login limit.
 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class RateLimitConfig {

    /** Trusted proxies in front of the gateway (0: the gateway receives clients directly). */
    @Value("${showcase.rate-limit.trusted-proxy-hops:0}")
    private final int trustedProxyHops;

    @Bean
    @Primary
    public KeyResolver userOrIpKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(Principal::getName)
                .map(name -> "user:" + name)
                .switchIfEmpty(Mono.fromSupplier(() -> "ip:" + clientIp(exchange)));
    }

    /**
     * By IP, for the login (there is no authenticated user yet). Its own prefix is necessary: Redis stores the
     * bucket by route and key, and on the login route the general limit also applies with the {@code ip:} key;
     * with the same key, the two limits would share a bucket with different parameters.
     */
    @Bean
    public KeyResolver loginKeyResolver() {
        return exchange -> Mono.just("login:" + clientIp(exchange));
    }

    String clientIp(ServerWebExchange exchange) {
        InetSocketAddress address = trustedProxyHops > 0
                ? XForwardedRemoteAddressResolver.maxTrustedIndex(trustedProxyHops).resolve(exchange)
                : exchange.getRequest().getRemoteAddress();
        return Optional.ofNullable(address)
                .map(socket -> socket.getAddress() != null ? socket.getAddress().getHostAddress()
                        : socket.getHostString())
                .orElse("unknown");
    }
}
