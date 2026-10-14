package com.showcase.customer.infrastructure.persistence.repository.impl;

import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.common.infrastructure.persistence.page.SpringDataPageAdapter;
import com.showcase.common.infrastructure.persistence.specification.FilterMapping;
import com.showcase.common.infrastructure.persistence.specification.GenericSpecificationBuilder;
import com.showcase.customer.domain.exception.CustomerErrorCode;
import com.showcase.customer.domain.model.Address;
import com.showcase.customer.domain.model.Customer;
import com.showcase.customer.domain.repository.CustomerRepository;
import com.showcase.common.domain.exception.ResourceNotFoundException;
import com.showcase.customer.infrastructure.persistence.entity.AddressEntity;
import com.showcase.customer.infrastructure.persistence.entity.CustomerEntity;
import com.showcase.customer.infrastructure.persistence.mapper.CustomerEntityMapper;
import com.showcase.customer.infrastructure.persistence.repository.CustomerJpaRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerRepositoryImpl implements CustomerRepository {

    /** {@code city} filtra por la ciudad de cualquiera de sus direcciones (join con la colección). */
    static final FilterMapping MAPPING = FilterMapping.of(Map.of("city", "addresses.city"));

    private final CustomerJpaRepository jpaRepository;
    private final CustomerEntityMapper mapper;
    private final GenericSpecificationBuilder specificationBuilder;

    public CustomerRepositoryImpl(CustomerJpaRepository jpaRepository, CustomerEntityMapper mapper,
                                  GenericSpecificationBuilder specificationBuilder) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.specificationBuilder = specificationBuilder;
    }

    @Override
    public Customer save(Customer customer) {
        CustomerEntity entity;
        if (customer.getId() == null) {
            entity = mapper.toEntity(customer);
        } else {
            entity = jpaRepository.findById(customer.getId()).orElseThrow(
                    () -> new ResourceNotFoundException(CustomerErrorCode.CUSTOMER_NOT_FOUND, customer.getId()));
            mapper.updateEntity(customer, entity);
        }
        syncAddresses(customer.getAddresses(), entity);
        return mapper.toDomain(jpaRepository.saveAndFlush(entity));
    }

    /**
     * Conserva las direcciones existentes (mismo id), borra las que ya no están (orphanRemoval) y añade
     * las nuevas. Volcar la lista entera borraría y reinsertaría todas, cambiando sus ids.
     */
    private static void syncAddresses(List<Address> addresses, CustomerEntity entity) {
        Set<Long> keptIds = addresses.stream().map(Address::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        entity.getAddresses().removeIf(existing -> !keptIds.contains(existing.getId()));
        Map<Long, AddressEntity> byId = entity.getAddresses().stream()
                .collect(Collectors.toMap(AddressEntity::getId, address -> address));
        for (Address address : addresses) {
            if (address.getId() == null) {
                entity.getAddresses().add(new AddressEntity(entity, address.getStreet(), address.getCity(),
                        address.getPostalCode(), address.getCountry(), address.isMain()));
            } else {
                byId.get(address.getId()).setMain(address.isMain());
            }
        }
    }

    @Override
    public Optional<Customer> findById(Long id) {
        return jpaRepository.findByIdAndActiveTrue(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Customer> findByUserId(Long userId) {
        return jpaRepository.findByUserIdAndActiveTrue(userId).map(mapper::toDomain);
    }

    @Override
    public PageResult<Customer> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive) {
        Page<CustomerEntity> page = jpaRepository.findAll(specificationBuilder.build(filters, MAPPING, includeInactive),
                SpringDataPageAdapter.toPageable(pageQuery, MAPPING));
        return SpringDataPageAdapter.toPageResult(page, mapper::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }

    @Override
    public boolean existsByEmailAndIdNot(String email, Long id) {
        return jpaRepository.existsByEmailAndIdNot(email, id);
    }

    @Override
    public boolean existsByUserId(Long userId) {
        return jpaRepository.existsByUserId(userId);
    }
}
