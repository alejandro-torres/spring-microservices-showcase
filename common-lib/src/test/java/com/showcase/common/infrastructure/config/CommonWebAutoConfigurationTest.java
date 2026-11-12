package com.showcase.common.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.showcase.common.infrastructure.exception.ConstraintViolationExceptionHandler;
import com.showcase.common.infrastructure.exception.DataAccessExceptionHandler;
import com.showcase.common.infrastructure.exception.ErrorCodeHttpStatusMapper;
import com.showcase.common.infrastructure.exception.ErrorResponseFactory;
import com.showcase.common.infrastructure.exception.GlobalExceptionHandler;
import com.showcase.common.infrastructure.i18n.ErrorMessageResolver;
import com.showcase.common.infrastructure.persistence.specification.GenericSpecificationBuilder;
import com.showcase.common.infrastructure.rest.filter.FilterCriteriaParser;
import com.showcase.common.infrastructure.security.JwtAuthoritiesConverter;
import com.showcase.common.infrastructure.security.ResourceServerSecurityCustomizer;
import com.showcase.common.infrastructure.security.RestAccessDeniedHandler;
import com.showcase.common.infrastructure.security.RestAuthenticationEntryPoint;
import com.showcase.common.infrastructure.security.SecurityExceptionHandler;
import java.time.Clock;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.LocaleResolver;

class CommonWebAutoConfigurationTest {

    private final WebApplicationContextRunner webContext = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CommonWebAutoConfiguration.class));

    @Test
    @DisplayName("registers error handling, messages and the filter parser in a web application")
    void webApplication_shouldRegisterAllBeans() {
        webContext.run(context -> assertThat(context)
                .hasSingleBean(GlobalExceptionHandler.class)
                .hasSingleBean(DataAccessExceptionHandler.class)
                .hasSingleBean(ConstraintViolationExceptionHandler.class)
                .hasSingleBean(ErrorResponseFactory.class)
                .hasSingleBean(ErrorMessageResolver.class)
                .hasSingleBean(ErrorCodeHttpStatusMapper.class)
                .hasSingleBean(FilterCriteriaParser.class)
                .hasSingleBean(GenericSpecificationBuilder.class)
                .hasSingleBean(RestAuthenticationEntryPoint.class)
                .hasSingleBean(RestAccessDeniedHandler.class)
                .hasSingleBean(SecurityExceptionHandler.class)
                .hasSingleBean(JwtAuthoritiesConverter.class)
                .hasSingleBean(ResourceServerSecurityCustomizer.class)
                .hasSingleBean(Clock.class)
                .hasBean(DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME));
    }

    @Test
    @DisplayName("uses English if the request has no Accept-Language or asks for an unsupported language")
    void localeResolver_shouldDefaultToEnglish() {
        webContext.run(context -> {
            var resolver = context.getBean(DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME, LocaleResolver.class);

            var withoutHeader = new MockHttpServletRequest();
            var spanish = new MockHttpServletRequest();
            spanish.addHeader("Accept-Language", "es-ES");
            var french = new MockHttpServletRequest();
            french.addHeader("Accept-Language", "fr-FR");

            assertThat(resolver.resolveLocale(withoutHeader).getLanguage()).isEqualTo(Locale.ENGLISH.getLanguage());
            assertThat(resolver.resolveLocale(spanish).getLanguage()).isEqualTo("es");
            assertThat(resolver.resolveLocale(french).getLanguage()).isEqualTo(Locale.ENGLISH.getLanguage());
        });
    }

    @Test
    @DisplayName("allows configuring the maximum filters and replacing any bean")
    void customization_shouldBeRespected() {
        var customClock = Clock.systemDefaultZone();
        webContext
                .withPropertyValues("showcase.filters.max-filters=3")
                .withBean(Clock.class, () -> customClock)
                .run(context -> {
                    assertThat(context.getBean(Clock.class)).isSameAs(customClock);
                    assertThat(context.getBean(FilterCriteriaParser.class))
                            .extracting("maxFilters").isEqualTo(3);
                });
    }

    @Test
    @DisplayName("registers nothing outside a web application")
    void nonWebApplication_shouldNotRegisterBeans() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CommonWebAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(GlobalExceptionHandler.class));
    }
}
