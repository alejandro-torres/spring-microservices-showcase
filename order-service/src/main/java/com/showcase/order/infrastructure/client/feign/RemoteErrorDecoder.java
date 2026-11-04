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
 * Translates the error responses of the other services. The remote body is never forwarded to the client:
 * 401/403 keep their meaning (the user's token is not valid or lacks permission) and the rest becomes
 * a generic 503, because it is a failure of the integration and not of the user's request.
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
                log.warn("Response {} from {}", response.status(), methodKey);
                yield new ExternalServiceException(CommonErrorCode.SERVICE_UNAVAILABLE);
            }
        };
    }
}
