package com.showcase.common.infrastructure.messaging.error;

import com.showcase.common.domain.exception.DomainException;
import com.showcase.common.domain.exception.ErrorCategory;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.amqp.support.converter.MessageConversionException;

/**
 * Decide si merece la pena reintentar un mensaje que ha fallado.
 * <ul>
 *   <li>No se reintenta lo que va a fallar siempre: un mensaje mal formado o una {@link DomainException} de
 *       negocio (datos no válidos, recurso inexistente, regla incumplida). Va directo a la DLQ.</li>
 *   <li>Sí se reintenta lo pasajero: base de datos caída, conflictos de bloqueo optimista, lock no conseguido,
 *       otro servicio no disponible y cualquier error inesperado.</li>
 * </ul>
 */
public final class MessagingExceptionClassifier {

    private static final Set<ErrorCategory> TRANSIENT = EnumSet.of(ErrorCategory.LOCKED,
            ErrorCategory.EXTERNAL_SERVICE, ErrorCategory.RATE_LIMITED, ErrorCategory.INTERNAL);

    private MessagingExceptionClassifier() {
    }

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
