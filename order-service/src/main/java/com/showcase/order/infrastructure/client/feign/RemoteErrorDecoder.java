package com.showcase.order.infrastructure.client.feign;

import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.ExternalServiceException;
import com.showcase.common.domain.exception.ForbiddenException;
import com.showcase.common.domain.exception.UnauthorizedException;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Traduce las respuestas de error de los otros servicios. El cuerpo remoto nunca se reenvía al cliente:
 * 401/403 conservan su significado (el token del usuario no sirve o no tiene permiso) y el resto se convierte
 * en un 503 genérico, porque es un fallo de la integración y no de la petición del usuario.
 */
public class RemoteErrorDecoder implements ErrorDecoder {

    private static final Logger log = LoggerFactory.getLogger(RemoteErrorDecoder.class);

    @Override
    public Exception decode(String methodKey, Response response) {
        return switch (response.status()) {
            case 404 -> new RemoteResourceNotFoundException(methodKey);
            case 401 -> new UnauthorizedException(CommonErrorCode.INVALID_TOKEN);
            case 403 -> new ForbiddenException(CommonErrorCode.FORBIDDEN);
            default -> {
                log.warn("Respuesta {} de {}", response.status(), methodKey);
                yield new ExternalServiceException(CommonErrorCode.SERVICE_UNAVAILABLE);
            }
        };
    }
}
