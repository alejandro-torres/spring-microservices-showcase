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
 * El gateway es la primera barrera: rechaza con 401 los tokens que no son válidos o que están revocados antes de
 * que la petición llegue a ningún servicio. El token se reenvía tal cual (cabecera {@code Authorization}) y cada
 * servicio vuelve a validarlo y aplica sus permisos con {@code @PreAuthorize}.
 * <p>
 * Públicas: registro, login y endpoints OAuth2 de auth-service, Swagger y health. Las peticiones CORS de
 * comprobación ({@code OPTIONS}) también, para que el gateway pueda responderlas.
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
                // Por defecto WebFlux pone no-referrer, y con esa política el navegador envía "Origin: null" en los
                // POST de formulario (el login): el filtro CORS los trata como de otro origen y responde 403.
                // strict-origin-when-cross-origin (el valor por defecto de los navegadores) envía el origen real y
                // a otros sitios solo el origen, nunca la ruta.
                .headers(headers -> headers.referrerPolicy(referrer -> referrer
                        .policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .build();
    }

    /** Firma y caducidad con el JWKS de auth-service; después, la lista de tokens revocados en Redis. */
    @Bean
    public ReactiveJwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
                                         String jwkSetUri, RevokedTokenChecker revokedTokenChecker) {
        return new RevocationCheckingJwtDecoder(NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build(),
                revokedTokenChecker);
    }
}
