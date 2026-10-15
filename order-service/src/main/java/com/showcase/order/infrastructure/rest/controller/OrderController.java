package com.showcase.order.infrastructure.rest.controller;

import static com.showcase.common.domain.filter.FilterOperator.BETWEEN;
import static com.showcase.common.domain.filter.FilterOperator.EQ;
import static com.showcase.common.domain.filter.FilterOperator.GTE;
import static com.showcase.common.domain.filter.FilterOperator.IN;
import static com.showcase.common.domain.filter.FilterOperator.LTE;

import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.infrastructure.rest.dto.response.PageResponseDTO;
import com.showcase.common.infrastructure.rest.filter.FilterCriteriaParser;
import com.showcase.common.infrastructure.rest.filter.FilterFields;
import com.showcase.common.infrastructure.rest.filter.FilterParameters;
import com.showcase.common.infrastructure.rest.filter.FilterableField;
import com.showcase.common.infrastructure.rest.page.PageQueryParser;
import com.showcase.order.application.service.OrderService;
import com.showcase.order.domain.model.Order;
import com.showcase.order.domain.model.OrderStatus;
import com.showcase.order.infrastructure.rest.dto.request.CreateOrderRequestDTO;
import com.showcase.order.infrastructure.rest.dto.request.UpdateOrderRequestDTO;
import com.showcase.order.infrastructure.rest.dto.response.OrderResponseDTO;
import com.showcase.order.infrastructure.rest.mapper.OrderRestMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    static final FilterFields FILTERS = FilterFields.of(
            FilterableField.of("status", OrderStatus.class, EQ, IN),
            FilterableField.of("customerId", Long.class, EQ),
            FilterableField.of("productId", Long.class, EQ),
            FilterableField.of("total", BigDecimal.class, GTE, LTE, BETWEEN),
            FilterableField.of("createdAt", Instant.class, GTE, LTE, BETWEEN));

    static final Set<String> SORT = Set.of("id", "total", "status", "createdAt");

    private final OrderService orderService;
    private final OrderRestMapper mapper;
    private final FilterCriteriaParser filterParser;

    @PreAuthorize("hasAuthority('order:write')")
    @PostMapping
    public ResponseEntity<OrderResponseDTO> create(@Valid @RequestBody CreateOrderRequestDTO request) {
        Order order = orderService.create(request.customerId(), mapper.toLines(request.items()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(order.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(order));
    }

    @PreAuthorize("hasAuthority('order:read')")
    @GetMapping("/{id}")
    public OrderResponseDTO findById(@PathVariable @Positive Long id) {
        return mapper.toResponse(orderService.findById(id));
    }

    /** P. ej. {@code ?filter=status:in:PENDING,CONFIRMED&filter=total:gte:100&sort=total&direction=desc}. */
    @PreAuthorize("hasAuthority('order:read') and (!#includeInactive or hasRole('ADMIN'))")
    @GetMapping
    public PageResponseDTO<OrderResponseDTO> findAll(
            FilterParameters filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sort,
            @RequestParam(defaultValue = "ASC") String direction,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return search(filterParser.parse(filter.values(), FILTERS),
                PageQueryParser.parse(page, size, sort, direction, SORT), includeInactive);
    }

    /** Pedidos de un cliente, del más reciente al más antiguo por defecto; admite los mismos filtros. */
    @PreAuthorize("hasAuthority('order:read')")
    @GetMapping("/by-customer/{customerId}")
    public PageResponseDTO<OrderResponseDTO> findByCustomer(
            @PathVariable @Positive Long customerId,
            FilterParameters filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "DESC") String direction) {
        List<FilterCriterion> filters = new ArrayList<>(filterParser.parse(filter.values(), FILTERS));
        filters.add(FilterCriterion.of("customerId", EQ, customerId));
        return search(filters, PageQueryParser.parse(page, size, sort, direction, SORT), false);
    }

    private PageResponseDTO<OrderResponseDTO> search(List<FilterCriterion> filters, PageQuery pageQuery,
                                                     boolean includeInactive) {
        return PageResponseDTO.from(orderService.findAll(filters, pageQuery, includeInactive), mapper::toResponse);
    }

    @PreAuthorize("hasAuthority('order:write')")
    @PutMapping("/{id}")
    public OrderResponseDTO update(@PathVariable @Positive Long id, @Valid @RequestBody UpdateOrderRequestDTO request) {
        return mapper.toResponse(orderService.update(id, mapper.toShippingAddress(request), request.version()));
    }

    @PreAuthorize("hasAuthority('order:write')")
    @PutMapping("/{id}/cancel")
    public OrderResponseDTO cancel(@PathVariable @Positive Long id) {
        return mapper.toResponse(orderService.cancel(id));
    }

    /** Borrado lógico de pedidos cerrados (cancelados o rechazados): es una tarea de administración. */
    @PreAuthorize("hasAuthority('order:write') and hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        orderService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
