package com.showcase.customer.infrastructure.persistence.mapper;

import com.showcase.customer.domain.model.Address;
import com.showcase.customer.domain.model.Customer;
import com.showcase.customer.infrastructure.persistence.entity.AddressEntity;
import com.showcase.customer.infrastructure.persistence.entity.CustomerEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/** Las direcciones se sincronizan a mano en el repositorio para conservar sus ids. */
@Mapper(componentModel = "spring")
public interface CustomerEntityMapper {

    Customer toDomain(CustomerEntity entity);

    Address toDomain(AddressEntity entity);

    @Mapping(target = "addresses", ignore = true)
    CustomerEntity toEntity(Customer customer);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "addresses", ignore = true)
    void updateEntity(Customer customer, @MappingTarget CustomerEntity entity);
}
