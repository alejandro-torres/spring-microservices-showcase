package com.showcase.gateway.infrastructure.config;

import com.showcase.common.infrastructure.i18n.ErrorMessageResolver;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** common-lib beans that its auto-configuration creates in MVC services and that the gateway (WebFlux) needs. */
@Configuration(proxyBeanMethods = false)
public class GatewayConfig {

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** common-lib's common error messages in English and Spanish. */
    @Bean
    public ErrorMessageResolver errorMessageResolver(MessageSource messageSource) {
        return new ErrorMessageResolver(messageSource);
    }
}
