package com.showcase.auth.infrastructure.rest.controller;

import static com.showcase.auth.infrastructure.rest.filter.AuthQueryFields.ROLE_FILTERS;
import static com.showcase.auth.infrastructure.rest.filter.AuthQueryFields.ROLE_SORT;

import com.showcase.auth.application.service.RoleService;
import com.showcase.auth.domain.model.Role;
import com.showcase.auth.infrastructure.rest.dto.request.CreateRoleRequestDTO;
import com.showcase.auth.infrastructure.rest.dto.request.UpdateRoleRequestDTO;
import com.showcase.auth.infrastructure.rest.dto.response.RoleResponseDTO;
import com.showcase.auth.infrastructure.rest.mapper.AuthRestMapper;
import com.showcase.common.infrastructure.rest.dto.response.PageResponseDTO;
import com.showcase.common.infrastructure.rest.filter.FilterCriteriaParser;
import com.showcase.common.infrastructure.rest.filter.FilterParameters;
import com.showcase.common.infrastructure.rest.page.PageQueryParser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
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
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;
    private final AuthRestMapper mapper;
    private final FilterCriteriaParser filterParser;

    @PreAuthorize("hasAuthority('role:write')")
    @PostMapping
    public ResponseEntity<RoleResponseDTO> create(@Valid @RequestBody CreateRoleRequestDTO request) {
        Role role = roleService.create(request.name(), request.description(), request.permissionIds());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(role.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(role));
    }

    @PreAuthorize("hasAuthority('role:read')")
    @GetMapping("/{id}")
    public RoleResponseDTO findById(@PathVariable @Positive Long id) {
        return mapper.toResponse(roleService.findById(id));
    }

    // Los registros borrados (includeInactive) solo los ve un ADMIN.
    @PreAuthorize("hasAuthority('role:read') and (!#includeInactive or hasRole('ADMIN'))")
    @GetMapping
    public PageResponseDTO<RoleResponseDTO> findAll(
            FilterParameters filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sort,
            @RequestParam(defaultValue = "ASC") String direction,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        var result = roleService.findAll(
                filterParser.parse(filter.values(), ROLE_FILTERS),
                PageQueryParser.parse(page, size, sort, direction, ROLE_SORT),
                includeInactive);
        return PageResponseDTO.from(result, mapper::toResponse);
    }

    @PreAuthorize("hasAuthority('role:write')")
    @PutMapping("/{id}")
    public RoleResponseDTO update(@PathVariable @Positive Long id, @Valid @RequestBody UpdateRoleRequestDTO request) {
        return mapper.toResponse(
                roleService.update(id, request.description(), request.permissionIds(), request.version()));
    }

    @PreAuthorize("hasAuthority('role:write')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        roleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
