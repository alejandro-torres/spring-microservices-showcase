package com.showcase.order.infrastructure.client.feign;

/**
 * The remote service responded 404. It does not leave the infrastructure layer: the client implementations
 * turn it into an empty {@code Optional}.
 */
public class RemoteResourceNotFoundException extends RuntimeException {

    public RemoteResourceNotFoundException(String methodKey) {
        super("Remote resource not found: " + methodKey);
    }
}
