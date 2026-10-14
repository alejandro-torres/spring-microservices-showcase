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
    @DisplayName("registra la gestión de errores, los mensajes y el parser de filtros en una aplicación web")
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
                .hasSingleBean(Clock.class)
                .hasBean(DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME));
    }

    @Test
    @DisplayName("usa el español si la petición no trae Accept-Language o pide un idioma no soportado")
    void localeResolver_shouldDefaultToSpanish() {
        webContext.run(context -> {
            var resolver = context.getBean(DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME, LocaleResolver.class);

            var withoutHeader = new MockHttpServletRequest();
            var english = new MockHttpServletRequest();
            english.addHeader("Accept-Language", "en-GB");
            var french = new MockHttpServletRequest();
            french.addHeader("Accept-Language", "fr-FR");

            assertThat(resolver.resolveLocale(withoutHeader).getLanguage()).isEqualTo("es");
            assertThat(resolver.resolveLocale(english).getLanguage()).isEqualTo(Locale.ENGLISH.getLanguage());
            assertThat(resolver.resolveLocale(french).getLanguage()).isEqualTo("es");
        });
    }

    @Test
    @DisplayName("permite configurar el máximo de filtros y sustituir cualquier bean")
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
    @DisplayName("no registra nada fuera de una aplicación web")
    void nonWebApplication_shouldNotRegisterBeans() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CommonWebAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(GlobalExceptionHandler.class));
    }
}
