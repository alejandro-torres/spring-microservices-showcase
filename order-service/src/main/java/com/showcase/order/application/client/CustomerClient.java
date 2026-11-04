package com.showcase.order.application.client;

import java.util.Optional;

/** Customer lookup in customer-service; same error contract as {@link ProductClient}. */
public interface CustomerClient {

    /** Empty if the customer does not exist or has been deleted. */
    Optional<CustomerInfo> findById(Long customerId);
}
