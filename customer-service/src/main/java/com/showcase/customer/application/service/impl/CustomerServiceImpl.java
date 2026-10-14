package com.showcase.customer.application.service.impl;

import com.showcase.common.domain.exception.DuplicateResourceException;
import com.showcase.common.domain.exception.ResourceNotFoundException;
import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.customer.application.service.CustomerService;
import com.showcase.customer.domain.exception.CustomerErrorCode;
import com.showcase.customer.domain.model.Address;
import com.showcase.customer.domain.model.Customer;
import com.showcase.customer.domain.repository.CustomerRepository;
import io.micrometer.observation.annotation.Observed;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerServiceImpl(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    /** El cliente y sus direcciones se guardan juntos (cascada): o todo o nada. */
    @Override
    @Transactional
    public Customer create(Long userId, String firstName, String lastName, String email, String phone,
                           List<Address> addresses) {
        Customer customer = Customer.create(userId, firstName, lastName, email, phone);
        if (userId != null && customerRepository.existsByUserId(userId)) {
            throw new DuplicateResourceException(CustomerErrorCode.USER_ALREADY_HAS_CUSTOMER, userId);
        }
        if (customerRepository.existsByEmail(customer.getEmail())) {
            throw new DuplicateResourceException(CustomerErrorCode.EMAIL_ALREADY_EXISTS, customer.getEmail());
        }
        if (addresses != null) {
            addresses.forEach(customer::addAddress);
        }
        return customerRepository.save(customer);
    }

    @Override
    public Customer findById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(CustomerErrorCode.CUSTOMER_NOT_FOUND, id));
    }

    @Override
    public Customer findByUserId(Long userId) {
        return customerRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(CustomerErrorCode.CUSTOMER_NOT_FOUND, userId));
    }

    @Override
    @Observed(name = "customer.search", contextualName = "buscar clientes")
    public PageResult<Customer> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive) {
        return customerRepository.findAll(filters, pageQuery, includeInactive);
    }

    @Override
    @Transactional
    public Customer update(Long id, String firstName, String lastName, String email, String phone, Long version) {
        Customer customer = findById(id);
        customer.checkVersion(version);
        customer.update(firstName, lastName, email, phone);
        if (customerRepository.existsByEmailAndIdNot(customer.getEmail(), id)) {
            throw new DuplicateResourceException(CustomerErrorCode.EMAIL_ALREADY_EXISTS, customer.getEmail());
        }
        return customerRepository.save(customer);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Customer customer = findById(id);
        customer.deactivate();
        customerRepository.save(customer);
    }

    @Override
    @Transactional
    public Customer addAddress(Long customerId, Address address) {
        Customer customer = findById(customerId);
        customer.addAddress(address);
        return customerRepository.save(customer);
    }

    @Override
    @Transactional
    public Customer removeAddress(Long customerId, Long addressId) {
        Customer customer = findById(customerId);
        customer.removeAddress(addressId);
        return customerRepository.save(customer);
    }

    @Override
    @Transactional
    public Customer setMainAddress(Long customerId, Long addressId) {
        Customer customer = findById(customerId);
        customer.setMainAddress(addressId);
        return customerRepository.save(customer);
    }
}
