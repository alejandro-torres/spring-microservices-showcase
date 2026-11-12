package com.showcase.common.infrastructure.i18n;

import com.showcase.common.domain.exception.ErrorCode;
import java.util.List;
import java.util.Locale;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * Resolves the error messages translated to the request's language.
 *
 * <p>It looks first in the service's own messages ({@code messages_*.properties}) and then
 * in {@code common-lib}'s common ones ({@code i18n/common-messages_*.properties}), so a
 * service can override any common message. English is the default language.
 */
@RequiredArgsConstructor
public class ErrorMessageResolver {

    public static final String COMMON_BASENAME = "i18n/common-messages";
    static final String DEFAULT_KEY = "error.default";

    /** common-lib's common messages, the same in all services: loaded only once. */
    private static final MessageSource COMMON_MESSAGES = createCommonMessageSource();

    @NonNull
    private final MessageSource serviceMessages;

    /** Message of an error code with its parameters. */
    public String resolve(ErrorCode errorCode, List<Object> args, Locale locale) {
        Object[] formattedArgs = toText(args);
        String message = find(errorCode.messageKey(), formattedArgs, locale);
        return message != null ? message : find(DEFAULT_KEY, null, locale);
    }

    /** Message of a field validation error (resolves its codes in order, e.g. NotBlank). */
    public String resolve(MessageSourceResolvable resolvable, Locale locale) {
        try {
            return serviceMessages.getMessage(withoutDefault(resolvable), locale);
        } catch (NoSuchMessageException ignored) {
            // Tested with the common messages.
        }
        try {
            return COMMON_MESSAGES.getMessage(resolvable, locale);
        } catch (NoSuchMessageException e) {
            return find(DEFAULT_KEY, null, locale);
        }
    }

    private String find(String key, Object[] args, Locale locale) {
        String message = serviceMessages.getMessage(key, args, null, locale);
        return message != null ? message : COMMON_MESSAGES.getMessage(key, args, null, locale);
    }

    // Parameters are passed as text so that MessageFormat does not format the ids (25000 → "25,000").
    private static Object[] toText(List<Object> args) {
        return args == null ? new Object[0] : args.stream().map(String::valueOf).toArray();
    }

    // Without the default message, so the service does not "resolve" with Hibernate Validator's text.
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
