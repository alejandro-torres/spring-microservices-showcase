package com.showcase.customer.domain.repository;

import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.customer.domain.model.Customer;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port to persist customers with their addresses.
 */
public interface CustomerRepository {

    Customer save(Customer customer);

    /** Active customers only, with their addresses. */
    Optional<Customer> findById(Long id);

    Optional<Customer> findByUserId(Long userId);

    PageResult<Customer> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    boolean existsByUserId(Long userId);
}
