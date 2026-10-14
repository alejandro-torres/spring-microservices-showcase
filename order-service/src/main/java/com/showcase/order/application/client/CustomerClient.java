package com.showcase.order.application.client;

import java.util.Optional;

/** Consulta de clientes en customer-service; mismo contrato de errores que {@link ProductClient}. */
public interface CustomerClient {

    /** Vacío si el cliente no existe o está borrado. */
    Optional<CustomerInfo> findById(Long customerId);
}
