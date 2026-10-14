package com.showcase.auth.infrastructure.rest.controller;

import com.showcase.auth.application.service.UserService;
import com.showcase.auth.domain.model.User;
import com.showcase.auth.infrastructure.rest.dto.request.RegisterUserRequestDTO;
import com.showcase.auth.infrastructure.rest.dto.response.UserResponseDTO;
import com.showcase.auth.infrastructure.rest.mapper.AuthRestMapper;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Registro público de usuarios (no requiere token). El nuevo usuario recibe el rol USER.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class RegistrationController {

    private final UserService userService;
    private final AuthRestMapper mapper;

    public RegistrationController(UserService userService, AuthRestMapper mapper) {
        this.userService = userService;
        this.mapper = mapper;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponseDTO> register(@Valid @RequestBody RegisterUserRequestDTO request) {
        User user = userService.register(request.username(), request.email(), request.password(),
                request.firstName(), request.lastName());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/users/{id}").buildAndExpand(user.getId()).toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(user));
    }
}
