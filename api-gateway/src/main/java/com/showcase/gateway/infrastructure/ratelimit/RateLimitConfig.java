package com.showcase.gateway.infrastructure.ratelimit;

import java.net.InetSocketAddress;
import java.security.Principal;
import java.util.Optional;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

/**
 * A quién se le cuentan las peticiones en el rate limiting (token bucket en Redis, compartido por todas las
 * réplicas del gateway):
 * <ul>
 *   <li>Usuario autenticado: su {@code sub} del JWT, así un usuario no puede saltarse el límite cambiando de IP.</li>
 *   <li>Rutas públicas (login, registro): la IP del cliente, la primera de {@code X-Forwarded-For} si el gateway
 *       está detrás de un proxy.</li>
 * </ul>
 * Los límites (fichas por segundo y ráfaga) se configuran por ruta en {@code application.yml}.
 */
@Configuration(proxyBeanMethods = false)
public class RateLimitConfig {

    static final String FORWARDED_FOR = "X-Forwarded-For";

    @Bean
    @Primary
    public KeyResolver userOrIpKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(Principal::getName)
                .map(name -> "user:" + name)
                .switchIfEmpty(Mono.fromSupplier(() -> "ip:" + clientIp(exchange.getRequest())));
    }

    /**
     * Por IP, para el login (todavía no hay usuario autenticado). El prefijo propio es necesario: Redis guarda el
     * cubo por ruta y clave, y en la ruta de login también actúa el límite general con la clave {@code ip:};
     * con la misma clave, los dos límites compartirían un cubo con parámetros distintos.
     */
    @Bean
    public KeyResolver loginKeyResolver() {
        return exchange -> Mono.just("login:" + clientIp(exchange.getRequest()));
    }

    static String clientIp(ServerHttpRequest request) {
        String forwardedFor = request.getHeaders().getFirst(FORWARDED_FOR);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return Optional.ofNullable(request.getRemoteAddress())
                .map(InetSocketAddress::getAddress)
                .map(address -> address.getHostAddress())
                .orElse("unknown");
    }
}
