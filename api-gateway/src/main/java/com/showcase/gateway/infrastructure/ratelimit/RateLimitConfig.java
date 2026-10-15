package com.showcase.gateway.infrastructure.ratelimit;

import java.net.InetSocketAddress;
import java.security.Principal;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * A quién se le cuentan las peticiones en el rate limiting (token bucket en Redis, compartido por todas las
 * réplicas del gateway):
 * <ul>
 *   <li>Usuario autenticado: su {@code sub} del JWT, así un usuario no puede saltarse el límite cambiando de IP.</li>
 *   <li>Rutas públicas (login, registro): la IP del cliente.</li>
 * </ul>
 * La IP es la de la conexión. {@code X-Forwarded-For} solo se tiene en cuenta si hay proxies de confianza delante
 * del gateway ({@code showcase.rate-limit.trusted-proxy-hops}), y solo la entrada que añadió el último de ellos:
 * si se leyera la cabecera sin más, un cliente podría inventarse una IP distinta en cada intento y saltarse el
 * límite del login.
 */
@Configuration(proxyBeanMethods = false)
public class RateLimitConfig {

    private final int trustedProxyHops;

    /** @param trustedProxyHops proxies de confianza delante del gateway (0: el gateway recibe a los clientes) */
    public RateLimitConfig(@Value("${showcase.rate-limit.trusted-proxy-hops:0}") int trustedProxyHops) {
        this.trustedProxyHops = trustedProxyHops;
    }

    @Bean
    @Primary
    public KeyResolver userOrIpKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(Principal::getName)
                .map(name -> "user:" + name)
                .switchIfEmpty(Mono.fromSupplier(() -> "ip:" + clientIp(exchange)));
    }

    /**
     * Por IP, para el login (todavía no hay usuario autenticado). El prefijo propio es necesario: Redis guarda el
     * cubo por ruta y clave, y en la ruta de login también actúa el límite general con la clave {@code ip:};
     * con la misma clave, los dos límites compartirían un cubo con parámetros distintos.
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
