package com.showcase.auth.infrastructure.rest.controller;

import static com.showcase.auth.infrastructure.rest.filter.AuthQueryFields.USER_FILTERS;
import static com.showcase.auth.infrastructure.rest.filter.AuthQueryFields.USER_SORT;

import com.showcase.auth.application.service.UserService;
import com.showcase.auth.domain.model.User;
import com.showcase.auth.infrastructure.rest.dto.request.ChangePasswordRequestDTO;
import com.showcase.auth.infrastructure.rest.dto.request.CreateUserRequestDTO;
import com.showcase.auth.infrastructure.rest.dto.request.UpdateUserRequestDTO;
import com.showcase.auth.infrastructure.rest.dto.response.UserResponseDTO;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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

/**
 * API de usuarios. Solo conoce la interfaz {@link UserService}: no hay lógica de negocio aquí.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthRestMapper mapper;
    private final FilterCriteriaParser filterParser;

    @PreAuthorize("hasAuthority('user:write')")
    @PostMapping
    public ResponseEntity<UserResponseDTO> create(@Valid @RequestBody CreateUserRequestDTO request) {
        User user = userService.create(request.username(), request.email(), request.password(), request.roleIds());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(user.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(user));
    }

    /** Datos del usuario autenticado: el {@code sub} del token es su username. */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public UserResponseDTO me(@AuthenticationPrincipal Jwt jwt) {
        return mapper.toResponse(userService.findByUsername(jwt.getSubject()));
    }

    /** El usuario autenticado cambia su propia contraseña. */
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal Jwt jwt,
                                               @Valid @RequestBody ChangePasswordRequestDTO request) {
        userService.changePassword(jwt.getSubject(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('user:read')")
    @GetMapping("/{id}")
    public UserResponseDTO findById(@PathVariable @Positive Long id) {
        return mapper.toResponse(userService.findById(id));
    }

    /**
     * Listado paginado con filtros dinámicos, p. ej.
     * {@code ?filter=role:eq:ADMIN&filter=username:like:ana&page=0&size=10&sort=createdAt&direction=DESC}.
     */
    // Los registros borrados (includeInactive) solo los ve un ADMIN.
    @PreAuthorize("hasAuthority('user:read') and (!#includeInactive or hasRole('ADMIN'))")
    @GetMapping
    public PageResponseDTO<UserResponseDTO> findAll(
            FilterParameters filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sort,
            @RequestParam(defaultValue = "ASC") String direction,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        var result = userService.findAll(
                filterParser.parse(filter.values(), USER_FILTERS),
                PageQueryParser.parse(page, size, sort, direction, USER_SORT),
                includeInactive);
        return PageResponseDTO.from(result, mapper::toResponse);
    }

    @PreAuthorize("hasAuthority('user:write')")
    @PutMapping("/{id}")
    public UserResponseDTO update(@PathVariable @Positive Long id, @Valid @RequestBody UpdateUserRequestDTO request) {
        return mapper.toResponse(userService.update(id, request.email(), request.enabled(), request.version()));
    }

    @PreAuthorize("hasAuthority('user:write')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('user:write')")
    @PutMapping("/{id}/unlock")
    public UserResponseDTO unlock(@PathVariable @Positive Long id) {
        return mapper.toResponse(userService.unlock(id));
    }

    @PreAuthorize("hasAuthority('user:write')")
    @PostMapping("/{id}/roles/{roleId}")
    public UserResponseDTO assignRole(@PathVariable @Positive Long id, @PathVariable @Positive Long roleId) {
        return mapper.toResponse(userService.assignRole(id, roleId));
    }

    @PreAuthorize("hasAuthority('user:write')")
    @DeleteMapping("/{id}/roles/{roleId}")
    public UserResponseDTO removeRole(@PathVariable @Positive Long id, @PathVariable @Positive Long roleId) {
        return mapper.toResponse(userService.removeRole(id, roleId));
    }
}
