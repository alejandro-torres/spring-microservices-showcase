package com.showcase.gateway.infrastructure.config;

import com.showcase.common.infrastructure.i18n.ErrorMessageResolver;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Beans de common-lib que en los servicios MVC crea su autoconfiguración y que el gateway (WebFlux) necesita. */
@Configuration(proxyBeanMethods = false)
public class GatewayConfig {

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Mensajes de error comunes de common-lib en español e inglés. */
    @Bean
    public ErrorMessageResolver errorMessageResolver(MessageSource messageSource) {
        return new ErrorMessageResolver(messageSource);
    }
}
