package com.showcase.auth.infrastructure.rest.controller;

import static com.showcase.auth.infrastructure.rest.filter.AuthQueryFields.PERMISSION_FILTERS;
import static com.showcase.auth.infrastructure.rest.filter.AuthQueryFields.PERMISSION_SORT;

import com.showcase.auth.application.service.PermissionService;
import com.showcase.auth.domain.model.Permission;
import com.showcase.auth.infrastructure.rest.dto.request.CreatePermissionRequestDTO;
import com.showcase.auth.infrastructure.rest.dto.request.UpdatePermissionRequestDTO;
import com.showcase.auth.infrastructure.rest.dto.response.PermissionResponseDTO;
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
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;
    private final AuthRestMapper mapper;
    private final FilterCriteriaParser filterParser;

    @PreAuthorize("hasAuthority('permission:write')")
    @PostMapping
    public ResponseEntity<PermissionResponseDTO> create(@Valid @RequestBody CreatePermissionRequestDTO request) {
        Permission permission = permissionService.create(request.name(), request.description());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(permission.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(permission));
    }

    @PreAuthorize("hasAuthority('permission:read')")
    @GetMapping("/{id}")
    public PermissionResponseDTO findById(@PathVariable @Positive Long id) {
        return mapper.toResponse(permissionService.findById(id));
    }

    // Los registros borrados (includeInactive) solo los ve un ADMIN.
    @PreAuthorize("hasAuthority('permission:read') and (!#includeInactive or hasRole('ADMIN'))")
    @GetMapping
    public PageResponseDTO<PermissionResponseDTO> findAll(
            FilterParameters filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sort,
            @RequestParam(defaultValue = "ASC") String direction,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        var result = permissionService.findAll(
                filterParser.parse(filter.values(), PERMISSION_FILTERS),
                PageQueryParser.parse(page, size, sort, direction, PERMISSION_SORT),
                includeInactive);
        return PageResponseDTO.from(result, mapper::toResponse);
    }

    @PreAuthorize("hasAuthority('permission:write')")
    @PutMapping("/{id}")
    public PermissionResponseDTO update(@PathVariable @Positive Long id,
                                        @Valid @RequestBody UpdatePermissionRequestDTO request) {
        return mapper.toResponse(permissionService.update(id, request.description(), request.version()));
    }

    @PreAuthorize("hasAuthority('permission:write')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        permissionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
