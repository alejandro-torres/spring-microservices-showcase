package com.showcase.customer.application.service;

import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.customer.domain.model.Address;
import com.showcase.customer.domain.model.Customer;
import java.util.List;

/**
 * Use cases for customers and their addresses (inbound port).
 */
public interface CustomerService {

    /** Creates the customer with their initial addresses in a single transaction. */
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
