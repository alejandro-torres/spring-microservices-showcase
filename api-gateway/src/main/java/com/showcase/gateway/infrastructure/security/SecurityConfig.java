package com.showcase.gateway.infrastructure.security;

import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.gateway.infrastructure.error.GatewayErrorWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;

/**
 * The gateway is the first barrier: it rejects with 401 tokens that are invalid or revoked before the request
 * reaches any service. The token is forwarded as is ({@code Authorization} header) and each service validates it
 * again and applies its permissions with {@code @PreAuthorize}.
 * <p>
 * Public: registration, login and auth-service's OAuth2 endpoints, Swagger and health. CORS preflight requests
 * ({@code OPTIONS}) too, so the gateway can answer them.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
public class SecurityConfig {

    static final String[] PUBLIC_PATHS = {
            "/oauth2/**", "/login", "/logout", "/.well-known/**", "/userinfo", "/connect/**", "/error",
            "/swagger-ui.html", "/swagger-ui/**", "/webjars/**", "/v3/api-docs/**", "/*/v3/api-docs",
            "/actuator/health/**", "/actuator/info", "/actuator/prometheus", "/fallback/**"};

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ReactiveJwtDecoder jwtDecoder,
                                                         GatewayErrorWriter errorWriter) {
        ServerAuthenticationEntryPoint entryPoint = (exchange, error) -> errorWriter.write(exchange,
                HttpStatus.UNAUTHORIZED, error instanceof InvalidBearerTokenException
                        ? CommonErrorCode.INVALID_TOKEN : CommonErrorCode.UNAUTHORIZED);
        ServerAccessDeniedHandler accessDeniedHandler = (exchange, error) ->
                errorWriter.write(exchange, HttpStatus.FORBIDDEN, CommonErrorCode.FORBIDDEN);
        return http
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()
                        .pathMatchers(PUBLIC_PATHS).permitAll()
                        .pathMatchers("/api/**").authenticated()
                        .anyExchange().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtDecoder(jwtDecoder))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
                // By default WebFlux sets no-referrer, and with that policy the browser sends "Origin: null" on
                // form POSTs (the login): the CORS filter treats them as cross-origin and responds 403.
                // strict-origin-when-cross-origin (the browsers' default) sends the real origin and,
                // to other sites, only the origin, never the path.
                .headers(headers -> headers.referrerPolicy(referrer -> referrer
                        .policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .build();
    }

    /** Signature and expiry with auth-service's JWKS; then, the list of revoked tokens in Redis. */
    @Bean
    public ReactiveJwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
                                         String jwkSetUri, RevokedTokenChecker revokedTokenChecker) {
        return new RevocationCheckingJwtDecoder(NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build(),
                revokedTokenChecker);
    }
}
