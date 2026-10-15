package com.showcase.customer.infrastructure.rest.controller;

import static com.showcase.common.domain.filter.FilterOperator.BETWEEN;
import static com.showcase.common.domain.filter.FilterOperator.EQ;
import static com.showcase.common.domain.filter.FilterOperator.GTE;
import static com.showcase.common.domain.filter.FilterOperator.LIKE;
import static com.showcase.common.domain.filter.FilterOperator.LTE;
import static com.showcase.common.domain.filter.FilterOperator.STARTS_WITH;

import com.showcase.common.infrastructure.rest.dto.response.PageResponseDTO;
import com.showcase.common.infrastructure.rest.filter.FilterCriteriaParser;
import com.showcase.common.infrastructure.rest.filter.FilterFields;
import com.showcase.common.infrastructure.rest.filter.FilterParameters;
import com.showcase.common.infrastructure.rest.filter.FilterableField;
import com.showcase.common.infrastructure.rest.page.PageQueryParser;
import com.showcase.customer.application.service.CustomerService;
import com.showcase.customer.domain.model.Customer;
import com.showcase.customer.infrastructure.rest.dto.request.AddressRequestDTO;
import com.showcase.customer.infrastructure.rest.dto.request.CreateCustomerRequestDTO;
import com.showcase.customer.infrastructure.rest.dto.request.UpdateCustomerRequestDTO;
import com.showcase.customer.infrastructure.rest.dto.response.CustomerResponseDTO;
import com.showcase.customer.infrastructure.rest.mapper.CustomerRestMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.Instant;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    static final FilterFields FILTERS = FilterFields.of(
            FilterableField.of("firstName", String.class, EQ, LIKE, STARTS_WITH),
            FilterableField.of("lastName", String.class, EQ, LIKE, STARTS_WITH),
            FilterableField.of("email", String.class, EQ, LIKE, STARTS_WITH),
            FilterableField.of("city", String.class, EQ, LIKE),
            FilterableField.of("userId", Long.class, EQ),
            FilterableField.of("createdAt", Instant.class, GTE, LTE, BETWEEN));

    static final Set<String> SORT = Set.of("id", "firstName", "lastName", "email", "createdAt");

    private final CustomerService customerService;
    private final CustomerRestMapper mapper;
    private final FilterCriteriaParser filterParser;

    @PreAuthorize("hasAuthority('customer:write')")
    @PostMapping
    public ResponseEntity<CustomerResponseDTO> create(@Valid @RequestBody CreateCustomerRequestDTO request) {
        Customer customer = customerService.create(request.userId(), request.firstName(), request.lastName(),
                request.email(), request.phone(), mapper.toDomain(request.addresses()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(customer.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(customer));
    }

    @PreAuthorize("hasAuthority('customer:read')")
    @GetMapping("/{id}")
    public CustomerResponseDTO findById(@PathVariable @Positive Long id) {
        return mapper.toResponse(customerService.findById(id));
    }

    @PreAuthorize("hasAuthority('customer:read')")
    @GetMapping("/by-user/{userId}")
    public CustomerResponseDTO findByUserId(@PathVariable @Positive Long userId) {
        return mapper.toResponse(customerService.findByUserId(userId));
    }

    /** P. ej. {@code ?filter=city:eq:Madrid&filter=lastName:startsWith:gar&sort=lastName}. */
    @PreAuthorize("hasAuthority('customer:read') and (!#includeInactive or hasRole('ADMIN'))")
    @GetMapping
    public PageResponseDTO<CustomerResponseDTO> findAll(
            FilterParameters filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sort,
            @RequestParam(defaultValue = "ASC") String direction,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        var result = customerService.findAll(filterParser.parse(filter.values(), FILTERS),
                PageQueryParser.parse(page, size, sort, direction, SORT), includeInactive);
        return PageResponseDTO.from(result, mapper::toResponse);
    }

    @PreAuthorize("hasAuthority('customer:write')")
    @PutMapping("/{id}")
    public CustomerResponseDTO update(@PathVariable @Positive Long id,
                                      @Valid @RequestBody UpdateCustomerRequestDTO request) {
        return mapper.toResponse(customerService.update(id, request.firstName(), request.lastName(), request.email(),
                request.phone(), request.version()));
    }

    @PreAuthorize("hasAuthority('customer:write')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        customerService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('customer:write')")
    @PostMapping("/{id}/addresses")
    public CustomerResponseDTO addAddress(@PathVariable @Positive Long id,
                                          @Valid @RequestBody AddressRequestDTO request) {
        return mapper.toResponse(customerService.addAddress(id, mapper.toDomain(request)));
    }

    @PreAuthorize("hasAuthority('customer:write')")
    @DeleteMapping("/{id}/addresses/{addressId}")
    public CustomerResponseDTO removeAddress(@PathVariable @Positive Long id, @PathVariable @Positive Long addressId) {
        return mapper.toResponse(customerService.removeAddress(id, addressId));
    }

    @PreAuthorize("hasAuthority('customer:write')")
    @PutMapping("/{id}/addresses/{addressId}/main")
    public CustomerResponseDTO setMainAddress(@PathVariable @Positive Long id, @PathVariable @Positive Long addressId) {
        return mapper.toResponse(customerService.setMainAddress(id, addressId));
    }
}
