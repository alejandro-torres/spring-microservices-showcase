package com.showcase.common.infrastructure.i18n;

import com.showcase.common.domain.exception.ErrorCode;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * Resuelve los mensajes de error traducidos al idioma de la petición.
 *
 * <p>Busca primero en los mensajes del propio servicio ({@code messages_*.properties}) y después
 * en los comunes de {@code common-lib} ({@code i18n/common-messages_*.properties}), de modo que un
 * servicio puede sobrescribir cualquier mensaje común. El español es el idioma por defecto.
 */
public class ErrorMessageResolver {

    public static final String COMMON_BASENAME = "i18n/common-messages";
    static final String DEFAULT_KEY = "error.default";

    private final MessageSource serviceMessages;
    private final MessageSource commonMessages;

    public ErrorMessageResolver(MessageSource serviceMessages) {
        this.serviceMessages = Objects.requireNonNull(serviceMessages, "serviceMessages");
        this.commonMessages = createCommonMessageSource();
    }

    /** Mensaje de un código de error con sus parámetros. */
    public String resolve(ErrorCode errorCode, List<Object> args, Locale locale) {
        Object[] formattedArgs = toText(args);
        String message = find(errorCode.messageKey(), formattedArgs, locale);
        return message != null ? message : find(DEFAULT_KEY, null, locale);
    }

    /** Mensaje de un error de validación de campo (resuelve sus códigos en orden, p. ej. NotBlank). */
    public String resolve(MessageSourceResolvable resolvable, Locale locale) {
        try {
            return serviceMessages.getMessage(withoutDefault(resolvable), locale);
        } catch (NoSuchMessageException ignored) {
            // Se prueba con los mensajes comunes.
        }
        try {
            return commonMessages.getMessage(resolvable, locale);
        } catch (NoSuchMessageException e) {
            return find(DEFAULT_KEY, null, locale);
        }
    }

    private String find(String key, Object[] args, Locale locale) {
        String message = serviceMessages.getMessage(key, args, null, locale);
        return message != null ? message : commonMessages.getMessage(key, args, null, locale);
    }

    // Los parámetros se pasan como texto para que MessageFormat no formatee los ids (25000 → "25.000").
    private static Object[] toText(List<Object> args) {
        return args == null ? new Object[0] : args.stream().map(String::valueOf).toArray();
    }

    // Sin el mensaje por defecto, para que el servicio no "resuelva" con el texto de Hibernate Validator.
    private static MessageSourceResolvable withoutDefault(MessageSourceResolvable source) {
        return new MessageSourceResolvable() {
            @Override
            public String[] getCodes() {
                return source.getCodes();
            }

            @Override
            public Object[] getArguments() {
                return source.getArguments();
            }
        };
    }

    private static MessageSource createCommonMessageSource() {
        var source = new ResourceBundleMessageSource();
        source.setBasename(COMMON_BASENAME);
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }
}
