package com.showcase.order.infrastructure.client.feign;

/**
 * El servicio remoto respondió 404. No sale de la capa de infraestructura: las implementaciones de los
 * clientes la convierten en un {@code Optional} vacío.
 */
public class RemoteResourceNotFoundException extends RuntimeException {

    public RemoteResourceNotFoundException(String methodKey) {
        super("Recurso remoto no encontrado: " + methodKey);
    }
}
