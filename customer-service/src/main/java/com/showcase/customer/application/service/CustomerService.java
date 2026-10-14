package com.showcase.customer.application.service;

import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.customer.domain.model.Address;
import com.showcase.customer.domain.model.Customer;
import java.util.List;

/**
 * Casos de uso de clientes y sus direcciones (puerto de entrada).
 */
public interface CustomerService {

    /** Crea el cliente con sus direcciones iniciales en una sola transacción. */
    Customer create(Long userId, String firstName, String lastName, String email, String phone,
                    List<Address> addresses);

    Customer findById(Long id);

    Customer findByUserId(Long userId);

    PageResult<Customer> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive);

    Customer update(Long id, String firstName, String lastName, String email, String phone, Long version);

    void delete(Long id);

    Customer addAddress(Long customerId, Address address);

    Customer removeAddress(Long customerId, Long addressId);

    Customer setMainAddress(Long customerId, Long addressId);
}
