package com.showcase.common.infrastructure.messaging.error;

import com.showcase.common.domain.exception.DomainException;
import com.showcase.common.domain.exception.ErrorCategory;
import java.util.EnumSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.amqp.support.converter.MessageConversionException;

/**
 * Decides whether a failed message is worth retrying.
 * <ul>
 *   <li>What will always fail is not retried: a malformed message or a business {@link DomainException}
 *       (invalid data, missing resource, broken rule). It goes straight to the DLQ.</li>
 *   <li>What is transient is retried: database down, optimistic locking conflicts, lock not acquired,
 *       another service unavailable and any unexpected error.</li>
 * </ul>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MessagingExceptionClassifier {

    private static final Set<ErrorCategory> TRANSIENT = EnumSet.of(ErrorCategory.LOCKED,
            ErrorCategory.EXTERNAL_SERVICE, ErrorCategory.RATE_LIMITED, ErrorCategory.INTERNAL);

    public static boolean isRetryable(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof MessageConversionException) {
                return false;
            }
            if (current instanceof DomainException domainException) {
                return TRANSIENT.contains(domainException.getErrorCode().category());
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return true;
    }
}
