package com.showcase.customer.infrastructure.rest.mapper;

import com.showcase.customer.domain.model.Address;
import com.showcase.customer.domain.model.Customer;
import com.showcase.customer.infrastructure.rest.dto.request.AddressRequestDTO;
import com.showcase.customer.infrastructure.rest.dto.response.AddressResponseDTO;
import com.showcase.customer.infrastructure.rest.dto.response.CustomerResponseDTO;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CustomerRestMapper {

    CustomerResponseDTO toResponse(Customer customer);

    AddressResponseDTO toResponse(Address address);

    /** Las direcciones nuevas se crean con su factoría del dominio, que valida los campos. */
    default Address toDomain(AddressRequestDTO request) {
        return Address.create(request.street(), request.city(), request.postalCode(), request.country());
    }

    default List<Address> toDomain(List<AddressRequestDTO> requests) {
        return requests == null ? List.of() : requests.stream().map(this::toDomain).toList();
    }
}
