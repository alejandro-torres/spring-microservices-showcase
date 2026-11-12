package com.showcase.common.infrastructure.config;

import com.showcase.common.infrastructure.exception.ConstraintViolationExceptionHandler;
import com.showcase.common.infrastructure.exception.DataAccessExceptionHandler;
import com.showcase.common.infrastructure.exception.ErrorCodeHttpStatusMapper;
import com.showcase.common.infrastructure.exception.ErrorResponseFactory;
import com.showcase.common.infrastructure.exception.GlobalExceptionHandler;
import com.showcase.common.infrastructure.i18n.ErrorMessageResolver;
import com.showcase.common.infrastructure.persistence.specification.GenericSpecificationBuilder;
import com.showcase.common.infrastructure.rest.filter.FilterCriteriaParser;
import com.showcase.common.infrastructure.rest.filter.FilterParametersArgumentResolver;
import com.showcase.common.infrastructure.security.JwtAuthoritiesConverter;
import com.showcase.common.infrastructure.security.RestAccessDeniedHandler;
import com.showcase.common.infrastructure.security.ResourceServerSecurityCustomizer;
import com.showcase.common.infrastructure.security.RestAuthenticationEntryPoint;
import com.showcase.common.infrastructure.security.SecurityErrorResponseWriter;
import com.showcase.common.infrastructure.security.SecurityExceptionHandler;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import tools.jackson.databind.json.JsonMapper;

/**
 * Automatically registers error handling, message translation and the filter parser in the MVC web
 * services that depend on common-lib. Any bean can be replaced by declaring your own.
 */
@AutoConfiguration(beforeName = "org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(DispatcherServlet.class)
public class CommonWebAutoConfiguration {

    static final Locale SPANISH = Locale.forLanguageTag("es");

    /**
     * Request language according to {@code Accept-Language}: English or Spanish, with English by default
     * (no header or an unsupported language).
     */
    @Bean(name = DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME)
    @ConditionalOnMissingBean(name = DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME)
    public LocaleResolver localeResolver() {
        var resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(Locale.ENGLISH);
        resolver.setSupportedLocales(List.of(Locale.ENGLISH, SPANISH));
        return resolver;
    }

    /** X-Instance-Id header with the replica serving each request. */
    @Bean
    @ConditionalOnMissingBean
    public InstanceIdResponseHeaderFilter instanceIdResponseHeaderFilter(
            @Value("${showcase.instance-id}") String instanceId) {
        return new InstanceIdResponseHeaderFilter(instanceId);
    }

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    public ErrorCodeHttpStatusMapper errorCodeHttpStatusMapper() {
        return new ErrorCodeHttpStatusMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    public ErrorMessageResolver errorMessageResolver(MessageSource messageSource) {
        return new ErrorMessageResolver(messageSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public ErrorResponseFactory errorResponseFactory(ErrorMessageResolver errorMessageResolver, Clock clock) {
        return new ErrorResponseFactory(errorMessageResolver, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler(ErrorResponseFactory errorResponseFactory,
                                                         ErrorCodeHttpStatusMapper errorCodeHttpStatusMapper) {
        return new GlobalExceptionHandler(errorResponseFactory, errorCodeHttpStatusMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public FilterCriteriaParser filterCriteriaParser(
            @Value("${showcase.filters.max-filters:" + FilterCriteriaParser.DEFAULT_MAX_FILTERS + "}") int maxFilters) {
        return new FilterCriteriaParser(maxFilters);
    }

    /** Registers the resolver of the {@code filter} parameters in Spring MVC. */
    @Configuration(proxyBeanMethods = false)
    static class FilterParametersConfiguration implements WebMvcConfigurer {

        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new FilterParametersArgumentResolver());
        }
    }

    /** 401/403 errors in the common format for services with Spring Security. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.security.web.AuthenticationEntryPoint")
    static class SecurityErrorsConfiguration {

        @Bean
        @ConditionalOnMissingBean
        SecurityErrorResponseWriter securityErrorResponseWriter(ErrorResponseFactory errorResponseFactory,
                                                                ObjectProvider<JsonMapper> jsonMapper,
                                                                LocaleResolver localeResolver) {
            return new SecurityErrorResponseWriter(errorResponseFactory,
                    jsonMapper.getIfAvailable(() -> JsonMapper.builder().build()), localeResolver);
        }

        @Bean
        @ConditionalOnMissingBean
        RestAuthenticationEntryPoint restAuthenticationEntryPoint(SecurityErrorResponseWriter writer) {
            return new RestAuthenticationEntryPoint(writer);
        }

        @Bean
        @ConditionalOnMissingBean
        RestAccessDeniedHandler restAccessDeniedHandler(SecurityErrorResponseWriter writer) {
            return new RestAccessDeniedHandler(writer);
        }

        @Bean
        @ConditionalOnMissingBean
        SecurityExceptionHandler securityExceptionHandler(ErrorResponseFactory errorResponseFactory) {
            return new SecurityExceptionHandler(errorResponseFactory);
        }
    }

    /** Conversion of JWT roles and permissions for services that validate tokens. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken")
    static class JwtAuthoritiesConfiguration {

        @Bean
        @ConditionalOnMissingBean
        JwtAuthoritiesConverter jwtAuthoritiesConverter() {
            return new JwtAuthoritiesConverter();
        }

        @Bean
        @ConditionalOnMissingBean
        ResourceServerSecurityCustomizer resourceServerSecurityCustomizer(
                JwtAuthoritiesConverter jwtAuthoritiesConverter,
                RestAuthenticationEntryPoint authenticationEntryPoint,
                RestAccessDeniedHandler accessDeniedHandler) {
            return new ResourceServerSecurityCustomizer(jwtAuthoritiesConverter, authenticationEntryPoint,
                    accessDeniedHandler);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(ConstraintViolationException.class)
    static class ValidationErrorsConfiguration {

        @Bean
        @ConditionalOnMissingBean
        ConstraintViolationExceptionHandler constraintViolationExceptionHandler(
                ErrorResponseFactory errorResponseFactory) {
            return new ConstraintViolationExceptionHandler(errorResponseFactory);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(Specification.class)
    static class JpaFiltersConfiguration {

        @Bean
        @ConditionalOnMissingBean
        GenericSpecificationBuilder genericSpecificationBuilder(
                @Value("${showcase.filters.unaccent:true}") boolean useUnaccent) {
            return new GenericSpecificationBuilder(useUnaccent);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(DataAccessException.class)
    static class DataAccessErrorsConfiguration {

        @Bean
        @ConditionalOnMissingBean
        DataAccessExceptionHandler dataAccessExceptionHandler(ErrorResponseFactory errorResponseFactory) {
            return new DataAccessExceptionHandler(errorResponseFactory);
        }
    }
}
