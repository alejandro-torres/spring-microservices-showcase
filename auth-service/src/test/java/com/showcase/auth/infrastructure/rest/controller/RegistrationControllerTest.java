package com.showcase.auth.infrastructure.rest.controller;

import static com.showcase.auth.testdata.AuthMother.user;
import static com.showcase.auth.testdata.AuthMother.userRole;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.showcase.auth.application.service.UserService;
import com.showcase.auth.domain.exception.EmailAlreadyExistsException;
import com.showcase.auth.infrastructure.rest.mapper.AuthRestMapperImpl;
import com.showcase.auth.infrastructure.security.config.ApiSecurityConfig;
import com.showcase.common.infrastructure.config.CommonWebAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RegistrationController.class)
@Import({CommonWebAutoConfiguration.class, ApiSecurityConfig.class, AuthRestMapperImpl.class})
class RegistrationControllerTest {

    private static final String BODY = """
            {"username":"ana","email":"ana@example.com","password":"Secreta-123"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("registra sin token y devuelve 201 con la ubicación del usuario")
    void register_withoutToken_shouldReturn201() throws Exception {
        given(userService.register("ana", "ana@example.com", "Secreta-123")).willReturn(user(10L, "ana", userRole()));

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, endsWith("/api/v1/users/10")))
                .andExpect(jsonPath("$.roles[0]").value("USER"));
    }

    @Test
    @DisplayName("devuelve 409 si el email ya está registrado")
    void register_withExistingEmail_shouldReturn409() throws Exception {
        given(userService.register("ana", "ana@example.com", "Secreta-123"))
                .willThrow(new EmailAlreadyExistsException("ana@example.com"));

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("valida el cuerpo y no admite elegir roles")
    void register_withInvalidBody_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"a","email":"ana@example.com","password":"corta"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verify(userService, never()).register(any(), any(), any());
    }
}
